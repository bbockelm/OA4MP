package edu.uiuc.ncsa.myproxy.oa4mp.oauth2.servlet;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2SE;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.RefreshTokenStore;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.clients.OA2Client;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.transactions.OA2ServiceTransaction;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.tx.TXRecord;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.vo.VirtualOrganization;
import edu.uiuc.ncsa.myproxy.oa4mp.server.util.ClientDebugUtil;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.impl.AccessTokenImpl;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.impl.RefreshTokenImpl;
import edu.uiuc.ncsa.oa4mp.delegation.common.token.impl.TokenUtils;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.*;
import edu.uiuc.ncsa.security.core.exceptions.InvalidAlgorithmException;
import edu.uiuc.ncsa.security.core.exceptions.InvalidSignatureException;
import edu.uiuc.ncsa.security.core.exceptions.TransactionNotFoundException;
import edu.uiuc.ncsa.security.core.exceptions.UnsupportedJWTTypeException;
import edu.uiuc.ncsa.security.core.util.BasicIdentifier;
import edu.uiuc.ncsa.security.core.util.MetaDebugUtil;
import edu.uiuc.ncsa.security.servlet.ServletDebugUtil;
import edu.uiuc.ncsa.security.util.jwk.JSONWebKeys;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.net.URI;

import static edu.uiuc.ncsa.myproxy.oa4mp.server.servlet.MyProxyDelegationServlet.getServiceEnvironment;
import static edu.uiuc.ncsa.oa4mp.delegation.oa2.server.claims.OA2Claims.JWT_ID;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 5/19/21 at  8:37 AM
 */
public class OA2TokenUtils {
    /**
     * Get the right set of keys, either from the service environment or the correct virtual organization.
     *
     * @param oa2se
     * @param client
     * @return
     */
    public static JSONWebKeys getKeys(OA2SE oa2se, OA2Client client) {
        VirtualOrganization vo = oa2se.getVO(client.getIdentifier());
        //CIL-974
        JSONWebKeys keys = ((OA2SE) getServiceEnvironment()).getJsonWebKeys();

        if (vo != null) {
            keys = vo.getJsonWebKeys();
            ServletDebugUtil.trace(OA2TokenUtils.class, "Got VO for client " + client.getIdentifierString());
        }
        return keys;
    }

    /**
     * Takes the subjectToken from the raw input (performing a base 32 decoding if needed)
     * and returns the access token.
     *
     * @param subjectToken
     * @param oa2se
     * @param keys
     * @return
     */
    public static AccessTokenImpl getAT(String subjectToken, OA2SE oa2se, JSONWebKeys keys, MetaDebugUtil debugger) {
        if (TokenUtils.isBase32(subjectToken)) {
            subjectToken = TokenUtils.b32DecodeToken(subjectToken);
        }
        AccessTokenImpl accessToken;
        JSONObject sciTokens;
        try {
            sciTokens = JWTUtil.verifyAndReadJWT(subjectToken, keys);
            //accessToken = tokenForge.getAccessToken(sciTokens.getString(JWT_ID));
            accessToken = new AccessTokenImpl(subjectToken, URI.create(sciTokens.getString(JWT_ID)));
        } catch (JSONException | IllegalArgumentException tt) {
            // didn't work, so now we assume it is regular token
            debugger.trace(OA2TokenUtils.class, "failed to parse access token as JWT:" + tt.getMessage());
            accessToken = (AccessTokenImpl) oa2se.getTokenForge().getAccessToken(subjectToken);
        } catch (InvalidSignatureException | InvalidAlgorithmException | UnsupportedJWTTypeException tt) {
            debugger.trace(OA2TokenUtils.class, "Failed to verify access token JWT: \"" + tt.getMessage());
            throw new OA2GeneralError(OA2Errors.INVALID_REQUEST,
                    "invalid access token",
                    HttpStatus.SC_BAD_REQUEST,
                    null);
        }
        debugger.trace(OA2TokenUtils.class, "access token from subject token = " + accessToken);

        OA2ServiceTransaction t;
        t = (OA2ServiceTransaction) oa2se.getTransactionStore().get(accessToken);


        if (t != null) {
            // Must present a valid token to get one.
            if (!t.isAccessTokenValid()) {
                debugger.info(OA2TokenUtils.class, "Access token invalid: " + t.summary());
                throw new OA2ATException(OA2Errors.INVALID_TOKEN,
                        "token invalid",
                        t.getRequestState());
            }
            if (accessToken.isExpired()) {
                debugger.info(OA2TokenUtils.class, "Access token expired: " + t.summary());
                throw new OA2ATException(OA2Errors.INVALID_TOKEN,
                        "token expired",
                        t.getRequestState());
            }
        }
        return accessToken;
    }

    /**
     * Takes the subjectToken from the raw input (performing a base 32 decoding if needed)
     * and returns the refresh token.
     *
     * @param subjectToken
     * @param oa2SE
     * @param keys
     * @return
     */
    public static RefreshTokenImpl getRT(String subjectToken, OA2SE oa2SE, JSONWebKeys keys, MetaDebugUtil debugger) {
        if (TokenUtils.isBase32(subjectToken)) {
            subjectToken = TokenUtils.b32DecodeToken(subjectToken);
        }
        RefreshTokenImpl refreshToken;
        OA2TokenForge tokenForge = (OA2TokenForge) oa2SE.getTokenForge();
        try {
            JSONObject tt = JWTUtil.verifyAndReadJWT(subjectToken, keys);
            refreshToken = new RefreshTokenImpl(subjectToken, URI.create(tt.getString(JWT_ID)));
        } catch (JSONException | IllegalArgumentException tt) {
            debugger.trace(OA2TokenUtils.class, "Failed to parse refresh token as JWT:" + tt.getMessage());
            refreshToken = tokenForge.getRefreshToken(subjectToken);
        } catch (InvalidSignatureException | InvalidAlgorithmException | UnsupportedJWTTypeException tt) {
            // This is benign and may just mean that the format of the token is not a JWT. Only flog it on debug
            debugger.trace(OA2TokenUtils.class, "Failed to verify refresh token JWT: \"" + tt.getMessage());
            throw new OA2GeneralError(OA2Errors.INVALID_REQUEST,
                    "invalid refresh token",
                    HttpStatus.SC_BAD_REQUEST,
                    null);
        }
        debugger.trace(OA2TokenUtils.class, "refresh token from subject token = " + refreshToken);
        OA2ServiceTransaction t;
        try {
            RefreshTokenStore zzz = (RefreshTokenStore) oa2SE.getTransactionStore(); // better to get a class cast exception here
            t = zzz.get(refreshToken);
        } catch (TransactionNotFoundException tnfx) {
            // No such token found for the refresh token.
            if (debugger instanceof ClientDebugUtil) {
                // CIL-1088. Don't fill up log with error message, print out small error message
                // when no transaction found with any client id. This lets us
                // track down malfunctioning clients and inform their owners.
                ClientDebugUtil clientDebugUtil = (ClientDebugUtil) debugger;
                oa2SE.getMyLogger().info("client " + clientDebugUtil.getClient().getIdentifierString() + ",  transaction not found for \"" + refreshToken + "\"");
            }
            debugger.trace(OA2TokenUtils.class, "Transaction not found for \"" + refreshToken.getJti() + "\"");
            throw new OA2ATException(OA2Errors.INVALID_GRANT, "invalid refresh token");
        } catch (Throwable tt) {
            // This is serious. It means that there was a problem getting the transaction vs. the transaction did not exist.
            debugger.error(OA2TokenUtils.class, "error getting refresh token:" + refreshToken.getJti() + " message:" + tt.getMessage());
            throw new OA2ATException(OA2Errors.INVALID_GRANT, "invalid refresh token");
        }
        if (t != null) {
            // Must present a valid token to get one.
            if (!t.isRefreshTokenValid()) {
                //MyProxyDelegationServlet.createDebugger(t.getOA2Client()).trace(OA2TokenUtils.class, "invalid refresh token \"" + refreshToken.getJti() + "\"");
                debugger.info(OA2TokenUtils.class, "Refresh token invalid: " + t.summary());

                throw new OA2ATException(OA2Errors.INVALID_GRANT,
                        "invalid refresh token",
                        t.getRequestState());
            }
            // we wait until here to check if it is expired, since we want to return the correct
            // type of error with the state of the transaction.
            if (refreshToken.isExpired()) {
//                MyProxyDelegationServlet.createDebugger(t.getOA2Client()).trace(OA2TokenUtils.class, "expired refresh token \"" + refreshToken.getJti() + "\"");
                debugger.info(OA2TokenUtils.class, "Refresh token expired: " + t.summary());
                throw new OA2ATException(OA2Errors.INVALID_GRANT,
                        "expired refresh token",
                        t.getRequestState());
            }
        }
        return refreshToken;
    }

    /**
     * Given an access token (and transaction if available), Find the actual transaction. This may involve a look up
     * in the tx store
     *
     * @param accessToken
     * @param oa2se
     * @return
     * @throws IOException
     */
    public static OA2ServiceTransaction getTransactionFromTX(OA2SE oa2se, AccessTokenImpl accessToken, MetaDebugUtil debugger) throws IOException {
        return getTransactionFromTX(oa2se, accessToken.getJti(), debugger);
    }

    public static OA2ServiceTransaction getTransactionFromTX(OA2SE oa2se, RefreshTokenImpl refreshToken, MetaDebugUtil debugger) throws IOException {
        return getTransactionFromTX(oa2se, refreshToken.getJti(), debugger);
    }

    protected static OA2ServiceTransaction getTransactionFromTX(OA2SE oa2se, URI jti, MetaDebugUtil debugger) throws IOException {
        TXRecord txRecord = (TXRecord) oa2se.getTxStore().get(BasicIdentifier.newID(jti));
        if (txRecord == null) {
            if (debugger == null) {
                ServletDebugUtil.trace(OA2TokenUtils.class, "No transaction found, no TXRecord found for token with id = " + jti);
            } else {
                debugger.info(OA2TokenUtils.class, "no transaction found");
            }
            throw new OA2GeneralError(OA2Errors.INVALID_TOKEN,
                    "token not found",
                    HttpStatus.SC_UNAUTHORIZED,
                    null);
        }
        if (!txRecord.isValid()) {
            if (debugger != null) {
                debugger.info(OA2TokenUtils.class, "invalid token");
            }
            throw new OA2ATException(OA2Errors.INVALID_TOKEN,
                    "invalid token",
                    null);
        }
        if (txRecord.getExpiresAt() < System.currentTimeMillis()) {
            if (debugger != null) {
                debugger.info(OA2TokenUtils.class, "expired token");
            }
            throw new OA2ATException(OA2Errors.INVALID_TOKEN,
                    "token expired",
                    null);
        }
        return (OA2ServiceTransaction) oa2se.getTransactionStore().get(txRecord.getParentID());
    }

}
