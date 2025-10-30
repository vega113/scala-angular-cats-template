const backendPort = process.env.BACKEND_PORT || 8080;
const backendHost = process.env.BACKEND_HOST || 'localhost';
const backendUrl = `http://${backendHost}:${backendPort}`;

console.log(`[proxy] Target backend: ${backendUrl}`);

module.exports = {
  "/api": {
    target: backendUrl,
    secure: false,
    logLevel: "debug",
    changeOrigin: true
  },
  "**": {
    target: backendUrl,
    secure: false,
    bypass: function (req) {
      if (req && req.headers && req.headers.accept && req.headers.accept.indexOf("html") !== -1) {
        console.log("[proxy] SPA fallback to index.html");
        return "/index.html";
      }
    }
  }
};
