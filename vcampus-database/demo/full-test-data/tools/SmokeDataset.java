import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.shop.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.concurrent.*;

/** 在数据库副本上启动真实服务，验证登录、查询及业务写入。 */
class SmokeDataset {
    static <T> T result(CompletableFuture<T> future) throws Exception {
        return future.get(30,TimeUnit.SECONDS);
    }
    static void require(boolean ok,String description) {
        if(!ok) throw new IllegalStateException(description);
        System.out.println("PASS "+description);
    }
    public static void main(String[] args) throws Exception {
        var runtime=ApplicationRuntime.create(()->DriverManager.getConnection(
                "jdbc:ucanaccess://"+args[0]+";immediatelyReleaseResources=true"),
                Path.of(args[1]),Clock.systemUTC());
        try(var c=DriverManager.getConnection("jdbc:ucanaccess://"+args[0]);var s=c.createStatement();
            var r=s.executeQuery("SELECT currentValue FROM tblNumberSequence WHERE sequenceKey='CAMPUS_CARD_GLOBAL'")) {
            r.next();require(r.getInt(1)==1000,"startup preserves sequence 1000");
        }
        try(var server=new SocketServer(0,4,20,runtime.router())) {
            var executor=Executors.newSingleThreadExecutor();
            var serving=executor.submit(()->{server.serve();return null;});
            try {
                for(String login:new String[]{"TESTADMIN","TESTTEACHER001","213260001","213260101","213260991"}) {
                    try(var connection=new ClientConnection("127.0.0.1",server.localPort())) {
                        connection.connect(Duration.ofSeconds(10));
                        var users=new UserClientService(connection,"bulk-smoke-"+login,Duration.ofSeconds(30));
                        var logged=result(users.login(login,"Test12345".toCharArray()));
                        require(logged.user().loginId().equals(login),"login "+login);
                        if(login.equals("213260991")) {
                            require(logged.mustChangePassword(),"first password change");
                            result(users.logout());
                            continue;
                        }
                        var courses=new CourseClientService(connection);
                        require(result(courses.listTerms()).size()>=3,"course terms "+login);
                        var library=new LibraryClientService(connection,Duration.ofSeconds(30));
                        require(result(library.searchBooks(new BookSearchQuery("",null,false,1,20))).total()>=490,"library catalog "+login);
                        var shop=new ShopClientService(connection,Duration.ofSeconds(30));
                        require(result(shop.home(new HomeProductQuery(null,null,ProductSortMode.SALES_DESC,1,20))).total()>100,"shop catalog "+login);
                        if(login.equals("213260001")) {
                            require(result(shop.getOwnedShop()).shopId().equals("bulk-shop-001"),"seller owns shop");
                            require(result(courses.getCurrentEnrollments()).stream().filter(e->"ACTIVE".equals(e.enrollmentStatus())).count()==3,"student active enrollment records");
                            require(result(courses.getCurrentEnrollments()).stream().filter(e->"DROPPED".equals(e.enrollmentStatus())).count()==1,"student dropped history");
                            result(courses.enroll(new EnrollCommand("bulk-offering-156")));
                            require(result(courses.getCurrentEnrollments()).stream().filter(e->"ACTIVE".equals(e.enrollmentStatus())).count()==4,"new course enrollment");
                            var loan=result(library.borrow(new BorrowBookCommand("bulk-copy-0001-2")));
                            result(library.returnBook(new ReturnBookCommand(loan.loanId(),loan.rowVersion())));
                            System.out.println("PASS borrow and return");
                        }
                        if(login.equals("213260101")) {
                            require(result(shop.getCart())!=null,"buyer cart");
                            result(shop.getPaidOrders());
                            System.out.println("PASS buyer order history");
                            var paid=result(shop.simulatePayment(new SimulatePaymentCommand(
                                    "bulk-payment-0001",PaymentChannel.WECHAT,PaymentAttemptStatus.SUCCEEDED)));
                            require(paid.status()==PaymentStatus.SUCCEEDED,"pending order payment");
                        }
                        result(users.logout());
                    }
                }
            } finally {
                server.close();
                executor.shutdownNow();
                executor.awaitTermination(10,TimeUnit.SECONDS);
            }
        }
        // 在副本上模拟编号递增，检查下一次初始化不会回退流水号。
        try(var c=DriverManager.getConnection("jdbc:ucanaccess://"+args[0]);var s=c.createStatement()) {
            s.executeUpdate("UPDATE tblNumberSequence SET currentValue=1001 WHERE sequenceKey='CAMPUS_CARD_GLOBAL'");
        }
        ApplicationRuntime.create(()->DriverManager.getConnection("jdbc:ucanaccess://"+args[0]),Path.of(args[1]),Clock.systemUTC());
        try(var c=DriverManager.getConnection("jdbc:ucanaccess://"+args[0]);var s=c.createStatement();
            var r=s.executeQuery("SELECT currentValue FROM tblNumberSequence WHERE sequenceKey='CAMPUS_CARD_GLOBAL'")) {
            r.next();require(r.getInt(1)==1001,"restart preserves advanced sequence");
        }
        System.out.println("SMOKE PASSED");
    }
}
