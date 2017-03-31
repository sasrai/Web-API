package valandur.webapi.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import valandur.webapi.WebAPI;
import valandur.webapi.cache.CachedCommand;
import valandur.webapi.cache.DataCache;
import valandur.webapi.json.JsonConverter;
import valandur.webapi.misc.Permission;
import valandur.webapi.misc.WebAPICommandSource;

import javax.servlet.http.HttpServletResponse;
import java.security.AccessControlException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CmdServlet extends WebAPIServlet {
    @Override
    @Permission(perm = "cmd")
    protected void handleGet(ServletData data) {
        String[] paths = data.getPathParts();

        if (paths.length == 0 || paths[0].isEmpty()) {
            data.setStatus(HttpServletResponse.SC_OK);
            data.addJson("commands", JsonConverter.toJson(DataCache.getCommands()));
            return;
        }

        String pName = paths[0];
        Optional<CachedCommand> cmd = DataCache.getCommand(pName);
        if (!cmd.isPresent()) {
            data.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        data.setStatus(HttpServletResponse.SC_OK);
        data.addJson("command", JsonConverter.toJson(cmd.get(), true));
    }

    @Override
    @Permission(perm = "cmd")
    protected void handlePost(ServletData data) {
        data.setStatus(HttpServletResponse.SC_OK);

        final JsonNode reqJson = (JsonNode) data.getAttribute("body");
        final List<String> allowCommands = (List<String>) data.getAttribute("cmds");

        if (!canRunCommand(reqJson, allowCommands)) {
            throw new AccessControlException("An attempt was made to execute an unauthorized command.");
        }

        if (!reqJson.isArray()) {
            data.addJson("result", runCommand(reqJson));
            return;
        }

        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        for (JsonNode node : reqJson) {
            JsonNode res = runCommand(node);
            arr.add(res);
        }
        data.addJson("results", arr);
    }

    private boolean canRunCommand(JsonNode node, List<String> cmds) {
        final String cmd = node.get("command").asText();

        return cmds == null || !cmd.contains("*") && cmds.contains(cmd.trim().split(" ")[0]);
    }

    private JsonNode runCommand(JsonNode node) {
        final String cmd = node.get("command").asText();
        final String name = node.has("name") ? node.get("name").asText() : WebAPI.NAME;
        final int waitTime = node.has("waitTime") ? node.get("waitTime").asInt() : 0;
        final int waitLines = node.has("waitLines") ? node.get("waitLines").asInt() : 0;

        final WebAPICommandSource src = new WebAPICommandSource(name, waitLines);

        try {
            CompletableFuture
                    .runAsync(() -> WebAPI.executeCommand(cmd, src), WebAPI.syncExecutor)
                    .get();

            if (waitLines > 0 || waitTime > 0) {
                synchronized (src) {
                    src.wait(waitTime > 0 ? waitTime : WebAPI.cmdWaitTime);
                }
            }

            return JsonConverter.toJson(src.getLines(), true);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }
}
