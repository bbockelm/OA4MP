package edu.uiuc.ncsa.oa4mp.delegation.oa2.client;

import edu.uiuc.ncsa.security.core.exceptions.GeneralException;
import edu.uiuc.ncsa.oa4mp.delegation.client.request.RFC7662Request;
import edu.uiuc.ncsa.oa4mp.delegation.client.request.RFC7662Response;
import edu.uiuc.ncsa.oa4mp.delegation.client.server.RFC7662Server;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.server.RFC7662Constants;
import edu.uiuc.ncsa.security.servlet.ServiceClient;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.util.HashMap;

/**
 * For RFC 7662 -- the introspection endpoint.
 * <p>Created by Jeff Gaynor<br>
 * on 5/18/21 at  6:02 PM
 */
public class RFC7662Server2 extends TokenAwareServer implements RFC7662Server, RFC7662Constants {
    public RFC7662Server2(ServiceClient serviceClient, String wellKnown, boolean oidcEnabled) {
        super(serviceClient, wellKnown, oidcEnabled);
    }

    public RFC7662Response processRFC7662Request(RFC7662Request request) {
        HashMap<String, Object> parameters = new HashMap<>();
        String token;
        String out;
        if (request.hasAccessToken()) {
            parameters.put(TOKEN_TYPE_HINT, TYPE_ACCESS_TOKEN);
            token = request.getAccessToken().getToken();
            parameters.put(TOKEN, token);
            out = getServiceClient().doPost(parameters, token);
        } else {
            parameters.put(TOKEN_TYPE_HINT, TYPE_REFRESH_TOKEN);
            token = request.getRefreshToken().getToken();
            parameters.put(TOKEN, token);
            // Have to use basic authorizatio if no bearer token.
            out = getServiceClient().doPost(parameters, request.getClient().getIdentifierString(),request.getClient().getSecret());
        }

        try {
            JSONObject jsonObject = JSONObject.fromObject(out);
            RFC7662Response response = new RFC7662Response();
            response.setResponse(jsonObject);
            return response;
        } catch (JSONException jsonException) {
            throw new GeneralException("Error parsing JSON from \"" + out + "\"");
        }
    }

}
