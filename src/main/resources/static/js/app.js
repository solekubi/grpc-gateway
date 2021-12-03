window.onload = function () {
  // Begin Swagger UI call region
  const ui = SwaggerUIBundle({
    url: "/swagger-ui/api-docs",
    dom_id: "#swagger-ui",
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
    layout: "StandaloneLayout",
  });
  // End Swagger UI call region
  window.ui = ui;

  connect();
};

function connect() {
  var socket = new SockJS("/ws",null, { timeout: 30000 });
  stompClient = Stomp.over(socket);
  stompClient.debug = null
  stompClient.connect({}, function (frame) {
    stompClient.subscribe("/topic/reload", function (greeting) {
      if(greeting.body === "true"){
         window.location.reload();
       }
    });
  });
}
