package valandur.webapi.handlers;

import com.google.gson.JsonObject;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;

public class WebAPIErrorHandler extends ErrorHandler {

    public WebAPIErrorHandler(Server server) {
        this.setServer(server);
    }

    @Override
    protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        if (message == null)
            message = HttpStatus.getMessage(code);

        JsonObject obj = new JsonObject();
        obj.addProperty("code", code);
        obj.addProperty("message", message);
        writer.write(obj.toString());
    }
}
