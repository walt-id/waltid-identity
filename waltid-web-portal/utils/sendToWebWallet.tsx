const sendToWebWallet = (walletUrl: String, path: String, requestUrl: String) => {
    let request = requestUrl.replaceAll("\n", "").trim()
    // window.location.href = `https://wallet.walt.id/${path}` + request.substring(request.indexOf('?'));
    window.location.href = `${walletUrl}/${path}` + request.substring(request.indexOf('?'));
}

export {sendToWebWallet};
