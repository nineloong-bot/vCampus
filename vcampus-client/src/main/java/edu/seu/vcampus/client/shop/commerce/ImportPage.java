package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Local XLSX selection, authoritative row preview, error export and confirmed draft creation. */
final class ImportPage {
    private final CommercePanel ui;
    private List<ImportRow> rows=List.of();
    private ImportPreview preview;
    private final JTable table=CommerceTheme.table(new String[]{"原始行","分组","商品","介绍","类目","图片","规格","价格","库存","默认","结果","错误 / 提示"},new Object[0][0]);
    private final JLabel summary=new JLabel("仅限普通白名单商品；Excel文件在本机解析");
    private final JLabel[] counts={CommerceTheme.heading("0",26),CommerceTheme.heading("0",26),CommerceTheme.heading("0",26),CommerceTheme.heading("0",26)};
    private JButton confirm,report,choose;
    ImportPage(CommercePanel ui){this.ui=ui;}
    void open(){
        JPanel main=new JPanel(new BorderLayout(8,8));main.setOpaque(false);
        choose=CommerceTheme.button("选择 XLSX",this::choose);
        JPanel introduction=CommerceTheme.form();
        JPanel hero=CommerceTheme.card(new java.awt.Color(0xedf3e9),20);
        hero.setLayout(new BoxLayout(hero,BoxLayout.Y_AXIS));
        hero.add(CommerceTheme.heading("批量创建商品草稿",20));
        hero.add(CommerceTheme.gap(10));
        introduction.add(hero);
        introduction.add(CommerceTheme.row(CommerceTheme.button("下载模板",()->template(false)),
                CommerceTheme.button("下载示例",()->template(true)),choose));
        JPanel stats=new JPanel(new java.awt.GridLayout(1,4,12,0));stats.setOpaque(false);
        String[] labels={"规格行数","商品组数","有效商品组","失败或受影响行"};
        for(int i=0;i<labels.length;i++){
            JPanel metric=CommerceTheme.card(new java.awt.Color(0xedf3e9),16);
            metric.setLayout(new BoxLayout(metric,BoxLayout.Y_AXIS));metric.add(counts[i]);metric.add(CommerceTheme.muted(labels[i]));stats.add(metric);
        }
        introduction.add(stats);
        main.add(introduction,BorderLayout.NORTH);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==11?330:100);
        main.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel footer=CommerceTheme.form();footer.add(summary);
        report=CommerceTheme.button("下载校验报告",this::report);report.setEnabled(false);
        confirm=CommerceTheme.primary(CommerceTheme.button("确认导入有效组",this::confirm));confirm.setEnabled(false);
        footer.add(CommerceTheme.row(CommerceTheme.button("返回商品管理",()->new SellerCatalogPage(ui).open()),
                CommerceTheme.button("取消导入",()->{confirm.setEnabled(false);ui.notice("导入已取消；校验报告仍可下载");}),report,confirm));
        ui.modal("Excel 批量导入",main,footer,ui::closeModal,1150);
    }
    private void choose(){
        JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("Excel 工作簿 (*.xlsx)","xlsx"));
        if(chooser.showOpenDialog(ui)!=JFileChooser.APPROVE_OPTION)return;
        Path file=chooser.getSelectedFile().toPath();choose.setEnabled(false);confirm.setEnabled(false);report.setEnabled(false);preview=null;
        ui.notice("正在本机读取文件…");
        new SwingWorker<List<ImportRow>,Void>(){
            @Override protected List<ImportRow> doInBackground() throws Exception{return XlsxCatalogFiles.read(file);}
            @Override protected void done(){
                if(!SwingUtilities.isDescendingFrom(table,ui))return;
                choose.setEnabled(true);
                try{rows=get();ui.fetch("SHOP2_IMPORT_PREVIEW",new ImportCommand("",rows),data->render((ImportPreview)data));}
                catch(Exception error){ui.failure(error);}
            }
        }.execute();
    }
    private void render(ImportPreview result){
        preview=result;var model=(javax.swing.table.DefaultTableModel)table.getModel();model.setRowCount(0);
        for(var item:result.rows()){
            var r=item.row();String state=switch(item.status()){case "VALID"->"有效";case "AFFECTED"->"同组受影响";default->"错误";};
            model.addRow(new Object[]{r.line(),r.group(),r.name(),r.description(),r.category(),r.imageId(),r.skuName(),r.price(),r.stock(),
                    r.defaultFlag(),state,String.join("；",item.errors())+(item.warning().isEmpty()?"":"；"+item.warning())});
        }
        summary.setText("规格 "+result.rowCount()+" 行 · 商品 "+result.groupCount()+" 组 · 有效 "+result.validGroups()+" 组 · 失败或受影响 "+result.failedRows()+" 行");
        int[] values={result.rowCount(),result.groupCount(),result.validGroups(),result.failedRows()};
        for(int i=0;i<values.length;i++)counts[i].setText(Integer.toString(values[i]));
        report.setEnabled(true);confirm.setEnabled(result.validGroups()>0);
    }
    private void confirm(){
        if(preview==null||!ui.confirm("将创建 "+preview.validGroups()+" 个商品草稿。确认导入？"))return;
        ui.write("SHOP2_IMPORT_CONFIRM",new ImportCommand("",rows),confirm,data->{
            ImportResult result=(ImportResult)data;render(result.preview());confirm.setEnabled(false);
            ui.notice("已创建 "+result.productIds().size()+" 个草稿，可返回商品管理完善并上架");
        });
    }
    private void template(boolean example){
        Path path=savePath(example?"商品导入示例.xlsx":"商品导入模板.xlsx");if(path==null)return;
        try{
            var sample=example?List.of(new ImportRow(2,"A","校园笔记本","课堂学习使用","ordinary","book","标准款","12.50","20","是"),
                    new ImportRow(3,"A","校园笔记本","课堂学习使用","ordinary","book","双本装","22","10","否")):List.<ImportRow>of();
            XlsxCatalogFiles.write(path,sample);ui.notice("已保存 "+path.getFileName());
        }catch(Exception error){ui.failure(error);}
    }
    private void report(){
        Path path=savePath("商品导入校验报告.csv");if(path==null)return;
        try{
            var output=new StringBuilder("\uFEFF原始行,分组,商品名称,商品介绍,准入类目,图片编号,规格名称,价格,库存,默认规格,结果,原因,提示\r\n");
            for(var item:preview.rows()){
                var r=item.row();var values=List.of(Integer.toString(r.line()),r.group(),r.name(),r.description(),r.category(),r.imageId(),
                        r.skuName(),r.price(),r.stock(),r.defaultFlag(),item.status(),String.join("；",item.errors()),item.warning());
                output.append(values.stream().map(ImportPage::csv).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
            }
            Files.writeString(path,output,StandardCharsets.UTF_8);ui.notice("校验报告已保存");
        }catch(Exception error){ui.failure(error);}
    }
    private Path savePath(String name){
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File(name));
        if(chooser.showSaveDialog(ui)!=JFileChooser.APPROVE_OPTION)return null;
        Path path=chooser.getSelectedFile().toPath();
        return Files.exists(path)&&!ui.confirm("文件已存在，确认覆盖？")?null:path;
    }
    private static String csv(String value){
        if(!value.isEmpty()&&"=+-@".indexOf(value.charAt(0))>=0)value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
