# Shop 图表导出文件

本目录中的图片由 `../shop-function-diagrams.md` 内的 Mermaid 图表生成。

## 目录结构

```text
generated/
├── png/                  # 适合直接插入 Word、PPT
├── svg/                  # 矢量图，缩放不失真
├── rendered-preview.md   # 使用 SVG 的 Markdown 预览
└── README.md             # 本文件
```

## 图片对应关系

| 功能图 | PNG | SVG |
|---|---|---|
| 买家购物主流程 | [buyer-shopping-flow.png](png/buyer-shopping-flow.png) | [buyer-shopping-flow.svg](svg/buyer-shopping-flow.svg) |
| 商城角色与功能 | [role-capability-map.png](png/role-capability-map.png) | [role-capability-map.svg](svg/role-capability-map.svg) |
| 跨店订单拆分 | [cross-shop-order-structure.png](png/cross-shop-order-structure.png) | [cross-shop-order-structure.svg](svg/cross-shop-order-structure.svg) |
| 商城核心状态流转 | [shop-state-transitions.png](png/shop-state-transitions.png) | [shop-state-transitions.svg](svg/shop-state-transitions.svg) |
| Shop 模块边界 | [shop-module-boundary.png](png/shop-module-boundary.png) | [shop-module-boundary.svg](svg/shop-module-boundary.svg) |

答辩 PPT 或 Word 文档优先使用 `png/`；需要无损缩放或后期编辑时使用 `svg/`。
