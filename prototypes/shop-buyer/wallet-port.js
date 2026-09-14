/* Read-only balance port consumed by account display. The virtual demo overrides
 * it in wallet-ui.js; production connects to the independent balance service.
 * getBalance(): Promise<{status:'available',balanceCents:number,currency:'CNY'}
 *                    | {status:'unavailable'}>
 * No credentials or account IDs are taken from the page; the real adapter must
 * use the authenticated session. Payment requires a separate server order API.
 */
window.WalletPort={async getBalance(){return {status:'unavailable'};}};
