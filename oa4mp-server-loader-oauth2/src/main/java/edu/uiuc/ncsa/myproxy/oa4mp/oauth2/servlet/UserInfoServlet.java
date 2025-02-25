package edu.uiuc.ncsa.myproxy.oa4mp.oauth2.servlet;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2SE;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.transactions.OA2ServiceTransaction;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.claims.IDTokenHandler;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.claims.PayloadHandlerConfigImpl;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.state.ScriptRuntimeEngineFactory;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.clients.OA2Client;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.tokens.UITokenUtils;
import edu.uiuc.ncsa.qdl.exceptions.AssertionException;
import edu.uiuc.ncsa.security.core.util.MetaDebugUtil;
import edu.uiuc.ncsa.oa4mp.delegation.server.ServiceTransaction;
import edu.uiuc.ncsa.oa4mp.delegation.server.request.IssuerResponse;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.impl.AccessTokenImpl;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.OA2ATException;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.OA2Errors;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.OA2RedirectableError;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.OA2Scopes;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.jwt.JWTRunner;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.jwt.ScriptRuntimeException;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.server.UII2;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.server.UIIRequest2;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.server.UIIResponse2;
import net.sf.json.JSONObject;
import org.apache.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static edu.uiuc.ncsa.myproxy.oa4mp.oauth2.tokens.UITokenUtils.getRawAT;
import static edu.uiuc.ncsa.oa4mp.delegation.oa2.OA2Constants.*;
import static edu.uiuc.ncsa.oa4mp.delegation.oa2.server.claims.OA2Claims.EXPIRATION;
import static edu.uiuc.ncsa.oa4mp.delegation.oa2.server.claims.OA2Claims.ISSUED_AT;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 10/4/13 at  11:09 AM
 */
public class UserInfoServlet extends BearerTokenServlet {
    @Override
    protected void doIt(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        // The access token is sent in the authorization header and should look like
        // Bearer oa4mp:...
        AccessTokenImpl at = UITokenUtils.getAT(getRawAT(request));
        TokenManagerServlet.State state = new TokenManagerServlet.State();
        OA2ServiceTransaction transaction = findTransaction(at, state);
        // Fix for CIL-1124
        if(!transaction.isAccessTokenValid()){
            throw new OA2RedirectableError(OA2Errors.ACCESS_DENIED,
                     "access denied", HttpStatus.SC_UNAUTHORIZED,
                     transaction.getRequestState(),
                     transaction.getCallback());
        }
        // CIL-1104 fix. Only give back user info if the original request asked for openid
        // We do not do this for subsequent requests because, since this is private information,
        // the user must consent to it, so this scope *has* to be in the initial request.
        if(!transaction.getScopes().contains(OA2Scopes.SCOPE_OPENID)){
            throw new OA2RedirectableError(OA2Errors.ACCESS_DENIED,
                     "access denied", HttpStatus.SC_UNAUTHORIZED,
                     transaction.getRequestState(),
                     transaction.getCallback());
        }
        OA2SE oa2SE = (OA2SE) getServiceEnvironment();
        MetaDebugUtil debugger = createDebugger(transaction.getOA2Client());
        if (!transaction.getFlowStates().userInfo) {
            throw new OA2RedirectableError(OA2Errors.ACCESS_DENIED,
                    "access denied", HttpStatus.SC_UNAUTHORIZED,
                    transaction.getRequestState(),
                    transaction.getCallback());
        }
        // if we get to here, then the access token checked out, so we can just get it and use it.

        // Need to look this up by its jti if its not a basic access token.
        OA2Client resolvedClient = OA2ClientUtils.resolvePrototypes(oa2SE, transaction.getOA2Client());

        UII2 uis = new UII2(oa2SE.getTokenForge(), getServiceEnvironment().getServiceAddress());
        UIIRequest2 uireq = new UIIRequest2(request, at);
        uireq.setUsername(getUsername(transaction));
        UIIResponse2 uiresp = (UIIResponse2) uis.process(uireq);
        // creates the token handler just to get the updated accounting information.
        IDTokenHandler idTokenHandler = new IDTokenHandler(new PayloadHandlerConfigImpl(
                resolvedClient.getIDTokenConfig(),
                oa2SE,
                transaction,
                null, // no token exchange record outside of token exchanges.
                null));
        idTokenHandler.refreshAccountingInformation();

        JWTRunner jwtRunner = new JWTRunner(transaction, ScriptRuntimeEngineFactory.createRTE(oa2SE, transaction, null, transaction.getOA2Client().getConfig()));
        OA2ClientUtils.setupHandlers(jwtRunner, oa2SE, transaction, resolvedClient,null, request);
        try {
            jwtRunner.doUserInfo();
        } catch (AssertionException assertionError) {
            debugger.trace(this, "assertion exception \"" + assertionError.getMessage() + "\"");
            throw new OA2ATException(OA2Errors.INVALID_REQUEST, assertionError.getMessage(), HttpStatus.SC_BAD_REQUEST, transaction.getRequestState());
        } catch (ScriptRuntimeException sre) {
            // Client threw an exception.
            debugger.trace(this, "script runtime exception \"" + sre.getMessage() + "\"");
            throw new OA2ATException(sre.getRequestedType(), sre.getMessage(), sre.getHttpStatus(), transaction.getRequestState());
        } catch (IllegalAccessException iax) {
            throw new OA2ATException(OA2Errors.UNAUTHORIZED_CLIENT,
                    "access denied",
                    transaction.getRequestState());
        } catch (Throwable throwable) {
            debugger.trace(this, "Unable to update claims on token refresh", throwable);
            debugger.warn(this, "Unable to update claims on token refresh: \"" + throwable.getMessage() + "\"");
        }
        //setupTokens(client, rtiResponse, oa2SE, t, jwtRunner);
        debugger.trace(this, "finished processing claims.");

        if (jwtRunner.hasATHandler()) {
            transaction.setUserMetaData(jwtRunner.getAccessTokenHandler().getClaims());
        } else {
            if (jwtRunner.hasIDTokenHander()) {
                transaction.setUserMetaData(jwtRunner.getIdTokenHandlerInterface().getClaims());
            }
        }

        getTransactionStore().save(transaction);
        uiresp.getUserInfo().getMap().putAll(stripClaims(transaction.getUserMetaData()));
        uiresp.write(response);
    }

    /**
     * This strips out claims that should not be returned, such as the nonce, but are part of the original
     * id token.
     *
     * @param json
     * @return
     */
    protected JSONObject stripClaims(JSONObject json) {
        JSONObject r = new JSONObject();
        r.putAll(json);// new json object so we don't lose information and so we don't get concurrent update error
        String[] x = new String[]{ISSUED_AT, NONCE, EXPIRATION, EXPIRES_IN, AUTHORIZATION_TIME};
        for (String y : x) {
            r.remove(y);
        }
        return r;
    }

    /**
     * Override this if needed.
     *
     * @param transaction
     * @return
     */
    protected String getUsername(ServiceTransaction transaction) {
        return transaction.getUsername();
    }

    // not implemented.
    @Override
    public ServiceTransaction verifyAndGet(IssuerResponse iResponse) throws IOException {
        return null;
    }


}
