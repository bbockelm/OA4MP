package edu.uiuc.ncsa.oa4mp.delegation.oa2.client;

import edu.uiuc.ncsa.security.core.exceptions.GeneralException;
import edu.uiuc.ncsa.oa4mp.delegation.client.request.RTRequest;
import edu.uiuc.ncsa.oa4mp.delegation.client.request.RTResponse;
import edu.uiuc.ncsa.oa4mp.delegation.client.server.RTServer;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.AccessToken;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.RefreshToken;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.impl.AccessTokenImpl;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.impl.RefreshTokenImpl;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.OA2Constants;
import edu.uiuc.ncsa.security.servlet.ServiceClient;
import net.sf.json.JSONObject;

import java.net.URI;
import java.util.HashMap;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 2/26/14 at  2:18 PM
 */
public class RTServer2 extends TokenAwareServer implements RTServer {

    public RTServer2(ServiceClient serviceClient, String wellknown, boolean oidcEnabled) {
        super(serviceClient, wellknown, oidcEnabled);
    }

    @Override
    public RTResponse processRTRequest(RTRequest rtRequest) {
        AccessToken accessToken = rtRequest.getAccessToken();
        RefreshToken refreshToken = rtRequest.getRefreshToken();
        if (refreshToken == null) {
            throw new GeneralException("Error: There is no refresh token, so it is not possible to refresh it.");
        }
        String raw = getRTResponse(getAddress(), rtRequest);
        JSONObject json = getAndCheckResponse(raw);
        String returnedAT = json.getString(OA2Constants.ACCESS_TOKEN);
        if (accessToken.getToken().equals(returnedAT)) {
            throw new IllegalArgumentException("Error: The returned access token from the server should not match the one in the request.");
        }
        String exp = json.getString(OA2Constants.EXPIRES_IN);
        if (exp == null || exp.length() == 0) {
            throw new IllegalArgumentException("Error: missing expires_in field from server");
        }
        long expiresIn = Long.parseLong(exp) * 1000;

        RefreshTokenImpl refreshTokenImpl2 = new RefreshTokenImpl(URI.create(json.getString(OA2Constants.REFRESH_TOKEN)));
        AccessTokenImpl newAT = new AccessTokenImpl(URI.create(returnedAT));
        //refreshTokenImpl2.setLifetime(expiresIn);
        RTResponse rtResponse = createResponse(newAT, refreshTokenImpl2);
        if (oidcEnabled) {
            JSONObject idToken = getAndCheckIDToken(json, rtRequest);
            rtResponse.setParameters(idToken);
        }
        return rtResponse;
    }

    protected String getRTResponse(URI uri,  RTRequest rtRequest) {
        HashMap map = new HashMap();
        map.put(OA2Constants.GRANT_TYPE, OA2Constants.REFRESH_TOKEN);
        map.put(OA2Constants.REFRESH_TOKEN, rtRequest.getRefreshToken().getToken());
        map.put(OA2Constants.CLIENT_ID, rtRequest.getClient().getIdentifierString());
        map.put(OA2Constants.CLIENT_SECRET, rtRequest.getClient().getSecret());
        map.putAll(rtRequest.getParameters());
        String response = getServiceClient().doGet(map);
        return response;
    }

    public RTResponse createResponse(AccessTokenImpl at, RefreshTokenImpl rt) {
        return new RTResponse(at, rt);
    }
}
