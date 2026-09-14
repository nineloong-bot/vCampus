package edu.seu.vcampus.client.shop.commerce;
/** Client-safe explanations for common authoritative validation failures. */
public final class CommerceMessages {
 private CommerceMessages() { }
 /** Maps stable protocol codes to concise user guidance. */
 public static String explain(String code) {
  if(code==null)return "连接异常，请稍后重试";
  if(code.contains("INSUFFICIENT_BALANCE"))return "余额不足，请先充值后重试";
  if(code.contains("AMOUNT"))return "请检查金额：充值大于0且不超过1000，最多两位小数";
  if(code.contains("QUOTE_CHANGED"))return "商品价格或库存已变化，请重新确认结算";
  if(code.contains("EXPIRED")||code.contains("SESSION"))return "登录已失效，请重新登录";
  if(code.contains("FORBIDDEN")||code.contains("DENIED"))return "当前账号无权执行此操作";
  if(code.contains("RETRY"))return "结果暂未确定，请保持原内容重试，系统会防止重复操作";
  if(code.contains("CONFLICT")||code.contains("VERSION"))return "数据已变化，请刷新后重试";
  if(code.contains("INITIAL_PASSWORD"))return "请先修改初始密码";
  return code;
 }
}
