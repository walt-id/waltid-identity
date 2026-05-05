const sendToWebWallet = (walletUrl: String, path: String, requestUrl: String) => {
    let request = requestUrl.replaceAll("\n", "").trim()
    // window.location.href = `https://wallet.walt.id/${path}` + request.substring(request.indexOf('?'));
    const url = `${walletUrl}/${path}` + request.substring(request.indexOf('?'));
    window.open(url, '_blank', 'noopener,noreferrer');
}

export {sendToWebWallet};
