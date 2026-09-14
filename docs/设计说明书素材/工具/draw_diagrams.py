# -*- coding: utf-8 -*-
"""生成《vCampus 虚拟校园系统软件设计说明书》所需的框图 / 流程图 / E-R 图。

输出目录：docs/设计说明书素材/图/*.png
依赖：matplotlib（使用系统微软雅黑字体渲染中文）。
"""
from __future__ import annotations

import os

import matplotlib

matplotlib.use("Agg")
import matplotlib.patches as mpatches
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties

FONT_PATH = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD_PATH = r"C:\Windows\Fonts\msyhbd.ttc"
FP = FontProperties(fname=FONT_PATH)
FPB = FontProperties(fname=FONT_BOLD_PATH)

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "图")
OUT_DIR = os.path.abspath(OUT_DIR)

# 配色
C_CLIENT = "#DCE9F7"
C_SERVER = "#DDEEDD"
C_DB = "#FBE7D0"
C_UI = "#EAF2FB"
C_NET = "#F3E2F3"
C_ACCENT = "#C9DEF5"
C_WARN = "#F9D9D9"
C_OK = "#D6EFD8"


def new_canvas(width=12.0, height=8.0):
    fig, ax = plt.subplots(figsize=(width, height), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis("off")
    return fig, ax


def box(ax, x, y, w, h, text, fc="#FFFFFF", ec="#333333", fs=10, bold=False, lw=1.2, radius=0.02):
    """以左上角 (x, y) 为基准绘制矩形框，y 轴向下增长由调用方换算。"""
    ax.add_patch(
        mpatches.FancyBboxPatch(
            (x, y), w, h,
            boxstyle=f"round,pad=0,rounding_size={radius * 100}",
            linewidth=lw, edgecolor=ec, facecolor=fc, mutation_aspect=1,
        )
    )
    ax.text(
        x + w / 2.0, y + h / 2.0, text,
        ha="center", va="center", fontproperties=(FPB if bold else FP),
        fontsize=fs, color="#1A1A1A", linespacing=1.5,
    )


def container(ax, x, y, w, h, title, fc="#F7F9FC", ec="#5A6B7B", fs=11):
    ax.add_patch(
        mpatches.FancyBboxPatch(
            (x, y), w, h,
            boxstyle="round,pad=0,rounding_size=1.2",
            linewidth=1.6, edgecolor=ec, facecolor=fc, linestyle="--",
        )
    )
    ax.text(x + w / 2.0, y + h - 2.6, title, ha="center", va="center",
            fontproperties=FPB, fontsize=fs, color=ec)


def arrow(ax, p1, p2, text=None, style="-|>", color="#33475B", lw=1.4, fs=8.5,
          ls="-", rad=0.0, tpos=0.5, tdy=1.6):
    if lw <= 0:
        return
    ax.annotate(
        "", xy=p2, xytext=p1,
        arrowprops=dict(arrowstyle=style, color=color, linewidth=lw,
                        linestyle=ls, shrinkA=0, shrinkB=0,
                        connectionstyle=f"arc3,rad={rad}"),
    )
    if text:
        tx = p1[0] + (p2[0] - p1[0]) * tpos
        ty = p1[1] + (p2[1] - p1[1]) * tpos + tdy
        ax.text(tx, ty, text, ha="center", va="center", fontproperties=FP,
                fontsize=fs, color="#33475B",
                bbox=dict(boxstyle="round,pad=0.18", fc="white", ec="none", alpha=0.9))


def title(ax, text, y=96.5):
    ax.text(50, y, text, ha="center", va="center", fontproperties=FPB,
            fontsize=14, color="#1A1A1A")


def save(fig, name):
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name)
    fig.savefig(path, bbox_inches="tight", facecolor="white", pad_inches=0.15)
    plt.close(fig)
    print("written:", path)


# ---------------------------------------------------------------- 图 3-1 总体架构
def diagram_architecture():
    fig, ax = new_canvas(12, 7.2)
    title(ax, "图 3-1  vCampus 虚拟校园系统总体架构")

    container(ax, 2, 12, 27, 76, "客户端  vCampusClient.jar", C_CLIENT)
    container(ax, 36.5, 12, 27, 76, "服务端  vCampusServer.jar", C_SERVER)
    container(ax, 71, 24, 27, 50, "数据存储", C_DB)

    box(ax, 4.5, 76, 22, 8, "Swing 界面层\n（MainFrame + CardLayout）", C_UI)
    box(ax, 4.5, 63, 22, 8, "客户端应用服务\n（ClientService 各模块）", C_UI)
    box(ax, 4.5, 50, 22, 8, "客户端通信层\nClientConnection / PendingRequests", C_UI)
    box(ax, 4.5, 22, 22, 10, "会话缓存\n（内存保存 sessionToken）", "#F2F2F2")

    box(ax, 39, 76, 22, 8, "Socket 接入 / 会话\nServerSocket · ClientSession")
    box(ax, 39, 63, 22, 8, "路由 / 权限\nMessageRouter · Authorization")
    box(ax, 39, 50, 22, 8, "业务应用服务\nhandler → service")
    box(ax, 39, 37, 22, 8, "Repository\n（数据访问层）")
    box(ax, 39, 22, 22, 10, "并发与事务\n业务线程池 · 资源锁 · 事务管理")

    box(ax, 73, 52, 23, 10, "Access 数据库\nvCampus.accdb\n（UCanAccess JDBC）", C_DB)
    box(ax, 73, 34, 23, 9, "表结构 / 索引\nschema + seed 脚本", C_DB)

    arrow(ax, (15.5, 76), (15.5, 71))
    arrow(ax, (15.5, 63), (15.5, 58))
    arrow(ax, (15.5, 50), (15.5, 32), style="-", ls=":")

    arrow(ax, (26.5, 55), (39, 55), None)
    arrow(ax, (39, 52.4), (26.5, 52.4), None)
    ax.text(32.75, 58.6, "Message\n请求", ha="center", va="center", fontproperties=FP,
            fontsize=8.5, color="#33475B")
    ax.text(32.75, 49.0, "响应", ha="center", va="center", fontproperties=FP,
            fontsize=8.5, color="#33475B")
    ax.text(32.75, 68.5, "TCP 长连接\nJava 对象流", ha="center", va="center",
            fontproperties=FP, fontsize=8.5, color="#8A3E8A")

    arrow(ax, (50, 76), (50, 71))
    arrow(ax, (50, 63), (50, 58))
    arrow(ax, (50, 50), (50, 45))
    arrow(ax, (50, 37), (50, 32))

    arrow(ax, (61, 41), (73, 42), "JDBC")
    arrow(ax, (61, 44), (73, 53), None)

    save(fig, "fig-3-1-总体架构.png")


# ---------------------------------------------------------------- 图 3-2 功能模块结构
def diagram_module_tree():
    fig, ax = new_canvas(13, 8.2)
    title(ax, "图 3-2  vCampus 功能模块结构图")

    box(ax, 33, 86, 34, 8, "vCampus 虚拟校园系统", C_ACCENT, bold=True, fs=12)

    cols = [
        ("用户管理", ["注册 / 注销", "登录 / 登出", "授权与角色", "账户状态管理", "安全审计"]),
        ("学籍管理", ["院系 / 专业 / 班级", "学生录取建档", "学号与一卡通号", "学籍异动", "信息核对审核"]),
        ("选课系统", ["学期与选课阶段", "课程库 / 教学班", "选课 / 退课 / 换课", "重修", "课表与成绩导入"]),
        ("图书馆", ["图书检索", "书目 / 馆藏管理", "借书 / 还书 / 续借", "借阅记录", "借阅规则与罚金"]),
        ("校园商城", ["商品浏览 / 搜索", "购物车 / 下单", "模拟支付", "卖家入驻与店铺", "管理员审核"]),
    ]
    n = len(cols)
    total_w = 96.0
    gap = 2.0
    w = (total_w - gap * (n + 1)) / n
    x0 = gap

    # 树干：根节点 → 模块 → 子功能
    trunk_y = 63.5          # 模块下方的横向汇流线
    spine_bottom = 33.0     # 每个模块的纵向干线终点
    item_top = 58.0         # 第一个子功能框顶边
    item_h = 7.0
    step = 9.5

    for i, (name, items) in enumerate(cols):
        cx = x0 + i * (w + gap)
        mid = cx + w / 2
        box(ax, cx, 70, w, 8, name, C_UI, bold=True, fs=11)
        arrow(ax, (mid, 86), (mid, 78))

        for j, it in enumerate(items):
            box(ax, cx, item_top - j * step, w, item_h, it, "#FFFFFF", fs=9.5)

        last_mid_y = item_top - (len(items) - 1) * step + item_h / 2
        ax.plot([mid, mid], [70, trunk_y], color="#5A6B7B", lw=1.0)
        ax.plot([mid, mid], [trunk_y, last_mid_y], color="#5A6B7B", lw=1.0)
        for j in range(len(items)):
            yy = item_top - j * step + item_h / 2
            ax.plot([mid, mid], [trunk_y, yy], color="#5A6B7B", lw=1.0)

    box(ax, 28, 12, 44, 8, "公共模块 · 网络模块 · 多线程模块 · 数据库设计", "#EFEFEF", bold=True, fs=11)
    ax.plot([50, 50], [12, 20], color="#AAAAAA", lw=1.0, ls=":")
    for i in range(n):
        mid = x0 + i * (w + gap) + w / 2
        ax.plot([mid, mid], [20, 20], color="#AAAAAA", lw=0)
    save(fig, "fig-3-2-功能模块结构.png")


# ---------------------------------------------------------------- 图 4-1 登录流程
def diagram_login():
    fig, ax = new_canvas(11.5, 8.8)
    title(ax, "图 4-1  用户登录与首次强制改密流程图")

    cx, w = 32, 36          # 主流程列
    rx = 72                 # 失败分支列

    box(ax, cx, 88, w, 6.5, "开始：启动客户端 ClientMain", C_ACCENT, bold=True, fs=10)
    box(ax, cx, 79, w, 6.5, "LoginFrame 输入一卡通号 / 密码")
    box(ax, cx, 70, w, 6.5, "ClientConnection 建立 Socket 连接")
    box(ax, cx, 61, w, 6.5, "发送 USER_LOGIN（携带 requestId）")
    box(ax, cx, 49, w, 8.5, "服务端校验：密码 PBKDF2 哈希\n账户状态 / 锁定状态", C_SERVER)
    box(ax, cx, 37, w, 8.5, "登录成功：生成会话令牌\n返回角色、权限与 mustChangePassword", C_OK)
    box(ax, cx, 25, w, 7.5, "mustChangePassword = TRUE ?", "#FFF3CD", bold=True)
    box(ax, 3, 13, 33, 7.5, "仅显示强制改密页面\n成功改密后旧会话失效", C_OK)
    box(ax, 63, 13, 34, 7.5, "进入 MainFrame\n按权限装配导航与模块页面", C_OK)

    box(ax, rx, 49, 25, 8.5, "返回失败（USER_* 错误码）", C_WARN, fs=9)
    box(ax, rx, 37, 25, 8.5, "提示错误、保留输入\n累计失败次数 → 账户锁定", C_WARN, fs=9)
    box(ax, 3, 1, 33, 7.5, "改密成功后清除本地会话\n返回登录页重新登录", "#EFEFEF", fs=9)

    arrow(ax, (50, 88), (50, 85.5))
    arrow(ax, (50, 79), (50, 76.5))
    arrow(ax, (50, 70), (50, 67.5))
    arrow(ax, (50, 61), (50, 57.5))

    arrow(ax, (cx + w, 53), (rx, 53))
    ax.text(69.5, 55.6, "校验失败", ha="center", va="center", fontproperties=FP,
            fontsize=8.5, color="#8A2B2B")
    arrow(ax, (50, 49), (50, 45.5))
    ax.text(53.0, 47.2, "校验通过", ha="center", va="center", fontproperties=FP,
            fontsize=8.5, color="#2B6B3A")
    arrow(ax, (rx + 12.5, 49), (rx + 12.5, 45.5))
    arrow(ax, (50, 37), (50, 32.5))
    arrow(ax, (72, 25.5), (72, 22.5), style="-", ls=":")

    arrow(ax, (cx + 6, 25), (19, 20.5))
    ax.text(40.0, 22.0, "是", ha="center", va="center", fontproperties=FP,
            fontsize=9, color="#8A6B00")
    arrow(ax, (cx + w - 6, 25), (80, 20.5))
    ax.text(60.0, 22.0, "否", ha="center", va="center", fontproperties=FP,
            fontsize=9, color="#2B6B3A")
    arrow(ax, (19, 13), (19, 8.5))
    save(fig, "fig-4-1-登录流程.png")


# ---------------------------------------------------------------- 图 5-1 学籍录取
def diagram_admission():
    fig, ax = new_canvas(12, 8.2)
    title(ax, "图 5-1  新生录取建档流程图（跨模块事务）", y=97.5)

    box(ax, 33, 86, 34, 7, "管理员提交录取信息（StudentAdmissionDialog）", C_ACCENT, bold=True, fs=10)
    box(ax, 33, 75, 34, 7, "参数校验：专业代码 / 入学年份 / 班号 / 身份信息")
    box(ax, 33, 64, 34, 7, "开启数据库事务 TransactionContext", "#FFF3CD", bold=True)

    box(ax, 2, 51, 30, 9, "① 锁定一卡通全局序列\nNUMBER_SEQUENCE:\nCAMPUS_CARD_GLOBAL", C_SERVER, fs=8)
    box(ax, 35, 51, 30, 9, "② 锁定班级学号序列\nNUMBER_SEQUENCE:\nSTUDENT_NUMBER:<专业><年><班>", C_SERVER, fs=8)
    box(ax, 68, 51, 30, 9, "③ 分配一卡通号与学号\n按固定格式生成唯一编号", C_SERVER, fs=8)

    box(ax, 2, 37, 30, 9, "④ 调用用户模块\nUserAccountProvisioningPort\n创建账户（初始密码 12345678）", "#E8E0F5", fs=8)
    box(ax, 35, 37, 30, 9, "⑤ 写入学生档案 tblStudent\nmustChangePassword = TRUE", C_SERVER, fs=8)
    box(ax, 68, 37, 30, 9, "⑥ 写入审计日志 tblAuditLog\n记录操作人与目标", C_SERVER, fs=8)

    box(ax, 12, 22, 30, 8, "全部成功 → 提交事务\n返回录取结果", C_OK, bold=True)
    box(ax, 58, 22, 30, 8, "任一步失败 → 整体回滚\n不消耗已分配编号", C_WARN, bold=True)

    box(ax, 33, 8, 34, 8, "返回响应：学生档案 + 一卡通号 + 学号", C_UI)

    arrow(ax, (50, 86), (50, 82))
    arrow(ax, (50, 75), (50, 71))
    arrow(ax, (50, 64), (50, 60))
    arrow(ax, (44, 60), (17, 60), style="-", ls=":")
    arrow(ax, (50, 60), (50, 51))
    arrow(ax, (65, 60), (83, 60), style="-", ls=":")
    arrow(ax, (50, 51), (50, 46))
    arrow(ax, (44, 46), (17, 46), style="-", ls=":")
    arrow(ax, (65, 46), (83, 46), style="-", ls=":")
    arrow(ax, (17, 37), (27, 30), "成功")
    arrow(ax, (50, 37), (50, 30))
    arrow(ax, (83, 37), (73, 30), "失败")
    arrow(ax, (27, 22), (50, 16))
    arrow(ax, (73, 22), (50, 16), style="-", ls=":")
    save(fig, "fig-5-1-新生录取建档流程.png")


# ---------------------------------------------------------------- 图 6-1 选课流程
def diagram_enroll():
    fig, ax = new_canvas(11, 8.6)
    title(ax, "图 6-1  学生选课流程图（含并发名额控制）")

    box(ax, 34, 89, 32, 7, "学生选择教学班并点击「选课」", C_ACCENT, bold=True, fs=10)
    box(ax, 34, 78, 32, 7, "发送 COURSE_ENROLL（携带 requestId）")

    box(ax, 34, 67, 32, 7, "① 校验选课阶段是否开放 / 学期是否有效", fs=9.5)
    box(ax, 34, 56, 32, 7, "② 校验学生资格与重修要求", fs=9.5)
    box(ax, 34, 45, 32, 7, "③ 校验与已选课程时间冲突", fs=9.5)
    box(ax, 34, 34, 32, 7, "④ 锁定资源 OFFERING:<offeringId>", "#FFF3CD", fs=9.5, bold=True)
    box(ax, 34, 23, 32, 7, "⑤ 校验容量 enrolledCount < capacity\n且未重复选课", fs=9.5)
    box(ax, 34, 12, 32, 7, "⑥ 写入 tblEnrollment，enrolledCount+1\n（rowVersion 乐观锁）", C_SERVER, fs=9.5)
    box(ax, 34, 1, 32, 7, "提交事务，返回选课成功与新课表", C_OK, bold=True, fs=10)

    box(ax, 70, 45, 28, 18,
        "任一校验失败：\nCOURSE_OFFERING_FULL\nCOURSE_SCHEDULE_CONFLICT\n"
        "COURSE_DUPLICATE_ENROLLMENT\nCOURSE_ENROLLMENT_NOT_OPEN …", C_WARN, fs=9)

    for y in (89, 78, 67, 56, 45, 34, 23, 12):
        arrow(ax, (50, y), (50, y - 4))
    arrow(ax, (66, 37.5), (70, 45), style="-", ls=":")
    save(fig, "fig-6-1-学生选课流程.png")


# ---------------------------------------------------------------- 图 7-1 借还书
def diagram_library():
    fig, ax = new_canvas(12.5, 7.8)
    title(ax, "图 7-1  图书借阅与归还流程图", y=97.5)

    container(ax, 3, 3, 44, 83, "借书流程", "#F2F8F2")
    container(ax, 53, 3, 44, 83, "还书流程", "#F7F3F8")

    steps_l = [
        "读者检索图书并选择可借副本",
        "提交 LIBRARY_BORROW 请求",
        "校验读者状态：停用 / 超期 / 借阅上限",
        "锁定 LIBRARY_USER:<userId>",
        "锁定 BOOK_COPY:<copyId>",
        "校验副本状态为可借（AVAILABLE）",
        "写入 tblBookLoan，副本置为 BORROWED",
    ]
    steps_r = [
        "馆员扫描 / 录入副本条码",
        "提交 LIBRARY_RETURN 请求",
        "校验借阅记录归属与状态",
        "锁定 BOOK_COPY 与 LOAN:<loanId>",
        "计算逾期天数与阶梯罚金",
        "判定归还状态：正常 / 损坏 / 遗失",
        "写入归还时间、罚金并释放副本",
    ]

    top = 72.0
    step = 9.5
    h = 7.0
    for x, steps, cx in ((5, steps_l, 25), (55, steps_r, 75)):
        for i, s in enumerate(steps):
            y = top - i * step
            fc = C_SERVER if 3 <= i <= 5 else "#FFFFFF"
            box(ax, x, y, 40, h, s, fc, fs=9.5)
            if i:
                arrow(ax, (cx, y + h + step - h), (cx, y + h))
        box(ax, x, 5, 40, 6, "提交事务 → 操作完成并更新借阅状态", C_OK, bold=True, fs=9.5)
        arrow(ax, (cx, top - 6 * step), (cx, 11))

    save(fig, "fig-7-1-借还书流程.png")


# ---------------------------------------------------------------- 图 8-1 商城下单支付
def diagram_shop():
    fig, ax = new_canvas(12.5, 8.0)
    title(ax, "图 8-1  校园商城买家下单与支付流程图")

    box(ax, 30, 88, 40, 7, "买家浏览商品 → 加入购物车（SHOP_CART_ADD）", C_ACCENT, bold=True, fs=10)
    box(ax, 30, 77, 40, 7, "发起结算 SHOP_CHECKOUT（按店铺拆分订单）")
    box(ax, 30, 66, 40, 7, "创建订单组 tblOrderGroup + 各店铺订单 tblOrder")
    box(ax, 30, 55, 40, 7, "创建支付单 tblPayment，锁定库存\nLOCK: PAYMENT / ORDER_GROUP / SKU")
    box(ax, 30, 44, 40, 7, "生成库存预留 tblInventoryReservation\n（ACTIVE，带过期时间）")
    box(ax, 30, 33, 40, 7, "买家选择渠道，SHOP_SIMULATE_PAYMENT")

    box(ax, 2, 20, 29, 9, "支付成功\n预留 → CONSUMED\n订单 → PAID", C_OK, fs=9.5, bold=True)
    box(ax, 35.5, 20, 29, 9, "支付失败\n预留 → RELEASED\n库存回滚", C_WARN, fs=9.5, bold=True)
    box(ax, 69, 20, 29, 9, "超时未支付\n定时任务释放预留\n订单 → CANCELLED", "#FFF3CD", fs=9.5, bold=True)

    box(ax, 17, 5, 66, 8,
        "订单进入 PAID；PREPARING / SHIPPED / COMPLETED 状态与 shippedAt / completedAt\n"
        "字段已在库中预留，发货与确认收货命令尚未实现（详见 8.3 类分析）",
        "#EFEFEF", fs=8.5)

    for y in (88, 77, 66, 55, 44, 33):
        arrow(ax, (50, y), (50, y - 4))
    arrow(ax, (44, 33), (18, 29))
    arrow(ax, (50, 33), (50, 29))
    arrow(ax, (56, 33), (82, 29))
    arrow(ax, (16.5, 20), (30, 13))
    arrow(ax, (50, 20), (50, 13))
    arrow(ax, (83.5, 20), (70, 13), style="-", ls=":")
    save(fig, "fig-8-1-商城下单支付流程.png")


# ---------------------------------------------------------------- 图 9-1 通信时序
def diagram_sequence():
    fig, ax = new_canvas(13, 6.6)
    title(ax, "图 10-1  Message 请求 / 响应时序图")

    actors = [
        ("Swing 页面", 8), ("客户端服务", 24), ("ClientConnection", 40),
        ("服务端 MessageRouter", 60), ("Handler", 76), ("Service / Repository", 92),
    ]
    for name, x in actors:
        box(ax, x - 7.5, 84, 15, 7, name, C_UI, bold=True, fs=9.5)
        ax.plot([x, x], [10, 84], color="#9AA7B4", lw=1.0, ls="--")

    msgs = [
        (8, 24, 76, "调用模块客户端服务"),
        (24, 40, 68, "封装 Message + requestId"),
        (40, 60, 60, "ObjectOutputStream.writeObject"),
        (60, 76, 52, "按 command 路由并鉴权"),
        (76, 92, 44, "调用业务服务 / 仓储"),
        (92, 76, 36, "返回结果对象"),
        (76, 60, 28, "ResponseBody 封装"),
        (60, 40, 20, "ObjectInputStream.readObject"),
        (40, 24, 12, "CompletableFuture 完成"),
    ]
    for x1, x2, y, t in msgs:
        arrow(ax, (x1, y), (x2, y), t, fs=8.5, tdy=2.0)
    arrow(ax, (24, 12), (8, 12), "SwingUtilities.invokeLater 更新界面", fs=8.5, tdy=-2.6)
    ax.text(50, 4, "连接双方只创建一次对象流；写操作在连接级互斥锁保护下串行发送",
            ha="center", va="center", fontproperties=FP, fontsize=9, color="#666666")
    save(fig, "fig-10-1-通信时序.png")


# ---------------------------------------------------------------- 图 10-1 并发模型
def diagram_concurrency():
    fig, ax = new_canvas(12.5, 7.2)
    title(ax, "图 11-1  服务端并发模型与线程结构", y=97.5)

    box(ax, 30, 84, 40, 8,
        "ServerSocket 接收线程（调用线程，backlog = 50）\nserverSocket.accept()", C_SERVER, bold=True, fs=10)
    box(ax, 30, 71, 40, 8,
        "有界任务队列 ArrayBlockingQueue\n容量 = server.maxConnections（默认 100）", "#FFF3CD", fs=9.5)
    box(ax, 30, 58, 40, 8,
        "工作线程池 ThreadPoolExecutor\ncore = max = server.workerThreads（默认 8）", C_UI, bold=True, fs=9.5)
    box(ax, 30, 45, 40, 8,
        "每连接一条读取循环线程：ClientConnection.read()\n→ MessageRouter.route()", C_UI, fs=9.5)
    box(ax, 2, 45, 24, 8,
        "连接级互斥锁\n保护 ObjectOutputStream\n写入与 flush", "#FFF3CD", fs=9)
    box(ax, 73, 45, 25, 8,
        "StripedResourceLockManager\n256 条带资源锁\n（resourceType + resourceId）", "#FFF3CD", fs=9)
    box(ax, 30, 31, 40, 8,
        "TransactionManager.inTransaction()：synchronized\n单事务串行提交，异常整体回滚", C_SERVER, bold=True, fs=9)
    box(ax, 73, 31, 25, 8, "Repository\nUCanAccess JDBC", C_UI, fs=9.5)
    box(ax, 30, 12, 40, 8, "Access 数据库  vCampus.accdb", C_DB, bold=True, fs=10.5)
    box(ax, 2, 12, 24, 8, "tblRequestDedup\n请求幂等去重（保留 24 小时）", "#EFEFEF", fs=9)

    arrow(ax, (50, 84), (50, 79))
    arrow(ax, (50, 71), (50, 66))
    arrow(ax, (50, 58), (50, 53))
    arrow(ax, (30, 49), (26, 49))
    arrow(ax, (70, 49), (73, 49))
    arrow(ax, (50, 45), (50, 39))
    arrow(ax, (70, 35), (73, 35))
    arrow(ax, (50, 31), (50, 20))
    arrow(ax, (26, 16), (30, 16))

    ax.text(50, 4.5,
            "超出队列容量时接收线程返回 COMMON_SERVER_BUSY 并关闭连接；"
            "业务写操作在资源锁内串行执行，事务提交由 TransactionManager 全局串行化。",
            ha="center", va="center", fontproperties=FP, fontsize=8.5, color="#5A6B7B")
    save(fig, "fig-11-1-并发模型.png")


# ---------------------------------------------------------------- 图 12-1 E-R 图
def diagram_er():
    fig, ax = new_canvas(16, 9.8)
    title(ax, "图 12-1  vCampus 数据库核心实体关系（E-R）图")

    def entity(x, y, w, n, name, pk, fields, fc="#FFFFFF"):
        """n = 字段数；高度按字段数自动计算，保证文字不溢出边框。"""
        h = 8.8 + n * 1.95
        ax.add_patch(mpatches.FancyBboxPatch(
            (x, y), w, h, boxstyle="round,pad=0,rounding_size=0.7",
            linewidth=1.3, edgecolor="#33475B", facecolor=fc))
        ax.text(x + w / 2, y + h - 2.1, name, ha="center", va="center",
                fontproperties=FPB, fontsize=8.8, color="#1A2A3A")
        ax.plot([x, x + w], [y + h - 4.0, y + h - 4.0], color="#33475B", lw=0.8)
        ax.text(x + 1.0, y + h - 6.0, pk, ha="left", va="center",
                fontproperties=FP, fontsize=7.5, color="#8A2B2B")
        for i, f in enumerate(fields):
            ax.text(x + 1.0, y + h - 8.1 - i * 1.95, f, ha="left", va="center",
                    fontproperties=FP, fontsize=7.5, color="#333333")
        return y + h

    # ---- 中心：学生 ----
    entity(43, 44, 22, 8, "tblStudent  学生", "PK studentId", [
        "userId → tblUser", "studentNumber（唯一）", "studentName / gender",
        "idDocumentNumber（唯一）", "classId → tblClass",
        "phone / email / 籍贯资料", "studentStatus / 入学信息", "rowVersion"])
    entity(43, 16, 22, 6, "tblStudentProfileApplication", "PK applicationId", [
        "studentId → tblStudent", "applicationStatus", "baseStudentVersion",
        "资料字段快照", "reviewerUserId / reviewedAt", "reviewComment"])

    # ---- 左上：用户与权限 ----
    entity(1, 63.6, 18, 8, "tblUser  用户", "PK userId", [
        "loginId（唯一）", "passwordHash / Salt", "passwordIterations",
        "roleCode → tblRole", "accountStatus", "mustChangePassword",
        "failedLoginCount", "rowVersion"])
    entity(1, 46.9, 18, 2, "tblRole  角色", "PK roleCode", [
        "roleName", "→ tblRolePermission"])
    entity(1, 26.3, 18, 4, "tblAuditLog  审计日志", "PK auditId", [
        "userId → tblUser", "actionCode", "targetType / targetId", "resultCode"])

    # ---- 左下：学籍组织 ----
    entity(21, 73.35, 18, 3, "tblDepartment  院系", "PK departmentId", [
        "departmentCode（唯一）", "departmentName", "isActive"])
    entity(21, 52.75, 18, 4, "tblMajor  专业", "PK majorId", [
        "departmentId → 院系", "majorCode（唯一）", "majorName", "isActive"])
    entity(21, 32.15, 18, 4, "tblClass  班级", "PK classId", [
        "majorId → 专业", "classCode（唯一）", "enrollmentYear / 班号", "isActive"])

    # ---- 右：课程与选课 ----
    entity(69, 75, 20, 2, "tblTerm  学期", "PK termId", [
        "termCode（唯一）", "termStatus"])
    entity(69, 54, 20, 4, "tblCourse  课程", "PK courseId", [
        "courseCode（唯一）", "courseName", "credit DECIMAL(4,1)", "totalHours"])
    entity(69, 31, 20, 4, "tblCourseOffering  教学班", "PK offeringId", [
        "termId / courseId", "teacherUserId", "capacity / enrolledCount",
        "offeringStatus"])
    entity(69, 8, 20, 4, "tblEnrollment  选课记录", "PK enrollmentId", [
        "offeringId / studentId", "enrollmentType", "enrollmentStatus",
        "UK(studentId, offeringId)"])

    def line(p1, p2, color="#7A8794", lw=1.1):
        ax.plot([p1[0], p2[0]], [p1[1], p2[1]], color=color, lw=lw,
                solid_capstyle="round")

    def head(p_from, p_to, color="#7A8794"):
        ax.annotate("", xy=p_to, xytext=p_from,
                    arrowprops=dict(arrowstyle="-|>", color=color, linewidth=1.1,
                                    shrinkA=0, shrinkB=0))

    def tag(x, y, text, color="#4A5A6A"):
        ax.text(x, y, text, ha="center", va="center", fontproperties=FP,
                fontsize=7.4, color=color,
                bbox=dict(boxstyle="round,pad=0.14", fc="white", ec="none", alpha=0.95))

    # 用户 ↔ 角色 ↔ 审计
    head((10, 63.6), (10, 59.6));  tag(13.6, 61.6, "N : 1")
    head((10, 46.9), (10, 42.9));  tag(13.6, 44.9, "1 : N")

    # 院系 → 专业 → 班级
    head((30, 73.35), (30, 69.35)); tag(33.6, 71.3, "1 : N")
    head((30, 52.75), (30, 48.75)); tag(33.6, 50.7, "1 : N")

    # 用户 → 学生（走顶部走廊，避开左侧其余实体）
    line((10, 88), (10, 92.5)); line((10, 92.5), (54, 92.5)); head((54, 92.5), (54, 68.4))
    tag(30, 94.4, "1 : 1  用户—学生")

    # 班级 → 学生
    head((39, 41.0), (43, 50.0)); tag(36.4, 44.6, "1 : N")

    # 学生 → 资料申请
    head((54, 44), (54, 36.5)); tag(57.8, 40.2, "1 : N")

    # 课程 → 教学班 → 选课记录
    head((79, 54), (79, 47.6));   tag(82.6, 50.8, "1 : N")
    head((79, 31), (79, 24.6));   tag(82.6, 27.8, "1 : N")

    # 学期 → 教学班（走右侧走廊）
    line((89, 81), (94.5, 81)); line((94.5, 81), (94.5, 39.3)); head((94.5, 39.3), (89, 39.3))
    tag(94.5, 87.5, "1 : N")

    # 选课记录 → 学生（走中心与右列之间的走廊）
    line((69, 16.3), (67, 16.3)); line((67, 16.3), (67, 56)); head((67, 56), (65, 56))
    tag(67.0, 61.5, "N : 1")

    ax.text(50, 2.6,
            "说明：内部主键为 36 位 UUID；一卡通号、学号、课程号、ISBN 为唯一业务键；"
            "可变业务表均含 rowVersion / createdAt / updatedAt；交易与审计记录不做物理删除。",
            ha="center", va="center", fontproperties=FP, fontsize=8.0, color="#5A6B7B")
    save(fig, "fig-12-1-ER图.png")


if __name__ == "__main__":
    diagram_architecture()
    diagram_module_tree()
    diagram_login()
    diagram_admission()
    diagram_enroll()
    diagram_library()
    diagram_shop()
    diagram_sequence()
    diagram_concurrency()
    diagram_er()
    print("all diagrams generated ->", OUT_DIR)
