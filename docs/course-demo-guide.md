# 课程模块 Demo 与局域网测试

## 单机学生选课

1. 使用 JDK 21 执行 `scripts/start-course-demo-server.sh`（Windows 使用同名 `.bat`）。
   第一次启动会创建 `data/course-demo.accdb`、安装课程表并种入两个教学班。
2. 新开终端执行 `scripts/start-course-demo-client.sh`。默认身份是
   `student-demo-1 / STUDENT`。
3. 在“教学班查询”选择一行并点击“选择教学班”；随后切换到“我的选课”和
   “我的课表”。页面切换会重新读取服务端，能看到刚刚持久化的结果。
4. 第二个学生可执行 `scripts/start-course-demo-client.sh student-demo-2 STUDENT`。
   管理端可执行 `scripts/start-course-demo-client.sh admin-demo ADMIN`。

若需要反复从空数据开始测试，先停止服务端，再备份或删除仅用于 Demo 的
`data/course-demo.accdb`；下次启动会重新创建。不要对正式 `vCampus.accdb` 执行此操作。

## 退改补阶段

正常选课和退改补不能在服务端时间上同时开放。停止 Demo 服务端，将
`config/course-demo.properties` 中 `demo.phase` 改为 `ADJUSTMENT`，再启动服务端。
已有 Demo 学期会使用乐观锁更新到退改补时间窗；学生客户端的“退改补”页面即可实际测试
补选、退选和原子改选。

## 多电脑局域网

1. 服务端电脑启动 Demo 服务，确认系统防火墙允许 TCP 8888 入站。
2. 在服务端电脑查询局域网 IPv4 地址（例如 `192.168.1.20`）。
3. 将每台客户端电脑的 `config/client.properties` 中 `server.host` 改为该 IPv4；
   `server.port` 保持 `8888`。
4. 每台电脑使用不同令牌启动，例如 `student-demo-1`、`student-demo-2`。同一数据库由唯一
   服务端持有，客户端电脑不复制或直接打开 `.accdb`。
5. 并发容量测试使用预置的“并发测试课程 / 抢课测试班”，其容量固定为 1。两台客户端都先
   搜索“并发测试”，选中该教学班并同时点击“选择教学班”；最终只允许一个成功，另一台应
   收到“教学班容量已满”提示。若该班已经被之前的测试占用，停止服务端并重建 Demo 数据库
   后再测。

演示身份只存在于 `CourseDemoServerMain`，不能用于正式部署。正式整合使用
`docs/course-runtime-integration.md` 中的用户模块与学籍模块适配方式。
