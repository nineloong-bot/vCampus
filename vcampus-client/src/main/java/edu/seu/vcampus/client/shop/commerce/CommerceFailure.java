package edu.seu.vcampus.client.shop.commerce;
/** Safe protocol failure retaining the server's human-readable validation detail. */
final class CommerceFailure extends RuntimeException {
 final String code;
 CommerceFailure(String code,String message){super(message);this.code=code;}
}
