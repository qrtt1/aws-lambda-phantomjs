package qty.aws.lambda;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.StringUtils;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

@SuppressWarnings("rawtypes")
public class Lambda implements RequestHandler<LinkedHashMap, String> {

    @Override
    public String handleRequest(LinkedHashMap input, Context context) {
        try {
            context.getLogger().log("[INPUT] " + input);
            JSONObject json = new JSONObject(input);

            String result = executeScreenCapture(json);
            if (StringUtils.isNullOrEmpty(result)) {
                return executeAnyCommand(context, json);
            }
            return result;

        } catch (Exception e) {
            return outputException(e);
        }
    }

    protected String executeScreenCapture(JSONObject json) throws JSONException {
        if ("screen-capture".equals(json.getString("cmd"))) {
            String url = json.getString("url");
            return new ScreenCapture().execute(json.getString("bucket"), url);
        }
        return null;
    }

    protected String executeAnyCommand(Context context, JSONObject json) throws FileNotFoundException, JSONException,
            IOException {
        ApplicationExecutor executor = new ApplicationExecutor(json.getString("cmd"), getArgs(json));
        String result = executor.execute(1000 * 30);
        for (String o : result.split("\n")) {
            context.getLogger().log("RESULT: " + o + "\n");
        }
        return "OK";
    }

    protected String[] getArgs(JSONObject json) throws JSONException {
        if (!json.has("args")) {
            return new String[0];
        }

        JSONArray array = json.getJSONArray("args");
        ArrayList<String> args = new ArrayList<String>();
        for (int i = 0; i < array.length(); i++) {
            args.add("" + array.get(i));
        }
        return args.toArray(new String[0]);
    }

    protected String outputException(Exception e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream p = new PrintStream(out);
        e.printStackTrace(p);
        return new String(out.toByteArray());
    }

}