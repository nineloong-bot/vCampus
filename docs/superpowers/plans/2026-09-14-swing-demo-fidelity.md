# Swing Demo fidelity implementation plan

Goal: Restore the approved prototypes/shop-buyer design in the production Swing presentation layer.
Architecture: Native Swing with custom rounded primitives, centered layered modal host, CSS-derived type/spacing/colors, unchanged Socket DTOs and server behavior.
Spec: prototypes/shop-buyer/*.css and *-ui.js, user screenshots and explicit approval to rebuild Swing against Demo.

- [x] Root: shared theme/components, 76px branded navbar, max1450px content, responsive modal overlay with dim backdrop and close/Escape, async view restoration, native-control styling.
- [x] Account task: rebuild my/account modal, application lifecycle and wallet cards/forms from corresponding Demo files; preserve all API handlers.
- [x] Catalog task: intro, toolbar, 4/5-column rounded cards with imagery/tick, selection help, fixed pagination; details and cart modal content parity.
- [x] Management task: seller/admin/order forms and tables hierarchy parity using common primitives, correct scrolling and no vertically stretched forms.
- [x] Root acceptance: regression tests for modal behavior/dimensions/in-flight restoration and meaningful state transitions; real-Socket screenshots at matching sizes for my/home/cart/wallet/seller/admin; inspect and iterate, test and rebuild client.

Constraints: JDK21, no backend changes; <=200 lines for new Java files; existing dirty workspace preserved; no commit/push. Shared new helper API will be communicated to agents. Maven executions serialized. User already approved the design and scope; no repeat approval gate needed.
