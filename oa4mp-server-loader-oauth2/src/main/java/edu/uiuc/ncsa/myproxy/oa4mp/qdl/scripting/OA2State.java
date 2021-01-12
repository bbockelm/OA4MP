package edu.uiuc.ncsa.myproxy.oa4mp.qdl.scripting;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2SE;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2ServiceTransaction;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.tx.TXRecord;
import edu.uiuc.ncsa.myproxy.oa4mp.qdl.QDLXMLConstants;
import edu.uiuc.ncsa.qdl.evaluate.MetaEvaluator;
import edu.uiuc.ncsa.qdl.evaluate.OpEvaluator;
import edu.uiuc.ncsa.qdl.module.ModuleMap;
import edu.uiuc.ncsa.qdl.state.ImportManager;
import edu.uiuc.ncsa.qdl.state.State;
import edu.uiuc.ncsa.qdl.state.SymbolStack;
import edu.uiuc.ncsa.qdl.statements.FunctionTable;
import edu.uiuc.ncsa.security.core.Identifier;
import edu.uiuc.ncsa.security.core.util.MyLoggingFacade;

import javax.servlet.http.HttpServletRequest;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
import java.util.List;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 10/9/20 at  4:45 PM
 */
public class OA2State extends State {
    public OA2State(ImportManager resolver,
                    SymbolStack symbolStack,
                    OpEvaluator opEvaluator,
                    MetaEvaluator metaEvaluator,
                    FunctionTable functionTable,
                    ModuleMap moduleMap,
                    MyLoggingFacade myLoggingFacade,
                    boolean isServerMode) {
        super(resolver, symbolStack, opEvaluator, metaEvaluator, functionTable, moduleMap, myLoggingFacade, isServerMode);
    }

    transient OA2ServiceTransaction transaction;
    transient OA2SE oa2se;
    transient HttpServletRequest request;

    public TXRecord getTxRecord() {
        return txRecord;
    }

    public void setTxRecord(TXRecord txRecord) {
        this.txRecord = txRecord;
    }

    transient TXRecord txRecord;

    public OA2ServiceTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(OA2ServiceTransaction transaction) {
        this.transaction = transaction;
    }

    public OA2SE getOa2se() {
        return oa2se;
    }

    public void setOa2se(OA2SE oa2se) {
        this.oa2se = oa2se;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    public List<Identifier> getAdminIDs() {
        return getOa2se().getPermissionStore().getAdmins(getClientID());
    }

    public Identifier getClientID() {
        return getTransaction().getClient().getIdentifier();
    }

    @Override
    public void readExtraXMLElements(XMLEvent xe, XMLEventReader xer) throws XMLStreamException {
        super.readExtraXMLElements(xe, xer);
        switch (xe.asStartElement().getName().getLocalPart()) {
            case QDLXMLConstants.TX_RECORD:
                txRecord = new TXRecord(null);
                txRecord.fromXML(xer);
                break;

        }
    }

    @Override
    public void writeExtraXMLElements(XMLStreamWriter xsw) throws XMLStreamException {
        super.writeExtraXMLElements(xsw);
        if(txRecord != null){
            txRecord.toXML(xsw);
        }
    }
}
