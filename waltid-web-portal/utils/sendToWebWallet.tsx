const sendToWebWallet = (walletUrl: String, path: String, requestUrl: String) => {
    let request = requestUrl.replaceAll("\n", "").trim()
    window.location.href = `${walletUrl}/${path}` + request.substring(request.indexOf('?')), '_blank';
}

export { sendToWebWallet };