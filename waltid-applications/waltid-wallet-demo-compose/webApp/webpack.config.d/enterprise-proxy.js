// Local dev only. The enterprise API rejects foreign Origin headers, so the
// webpack server forwards wallet calls and drops that header.
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/auth", "/v1", "/v2"],
        target: "http://waltid.enterprise.localhost:3000",
        changeOrigin: true,
        onProxyReq: function (proxyReq) {
            proxyReq.removeHeader("origin");
        },
    },
];
