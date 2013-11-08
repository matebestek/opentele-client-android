package dk.silverbullet.telemed.questionnaire.node;

import android.app.ProgressDialog;
import android.util.Log;
import com.google.gson.Gson;
import dk.silverbullet.telemed.questionnaire.Questionnaire;
import dk.silverbullet.telemed.questionnaire.element.ListViewElement;
import dk.silverbullet.telemed.questionnaire.element.TextViewElement;
import dk.silverbullet.telemed.questionnaire.expression.Variable;
import dk.silverbullet.telemed.questionnaire.skema.*;
import dk.silverbullet.telemed.rest.ReminderTask;
import dk.silverbullet.telemed.rest.RetrieveMessageListTask;
import dk.silverbullet.telemed.rest.RetrieveRecipientsTask;
import dk.silverbullet.telemed.rest.bean.message.MessageRecipient;
import dk.silverbullet.telemed.rest.bean.message.Messages;
import dk.silverbullet.telemed.rest.listener.MessageListListener;
import dk.silverbullet.telemed.rest.listener.MessageWriteListener;
import dk.silverbullet.telemed.utils.Util;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class IOMenuNode extends IONode implements MessageListListener, MessageWriteListener {

    private static final String TAG = Util.getTag(IOMenuNode.class);
    private static final String MENU_TEXT_EDIT_SERVER_URL = "Ret server-URL";
    private static final String MENU_TEXT_SHOW_UPLOAD_DEBUG = "Vis Upload-debug-node?";
    private static final String MENU_TEXT_SET_ALARM = "Sæt alarm";

    private Node nextNode;
    private Variable<String> menu;

    private Map<String, String> skemaer = new LinkedHashMap<String, String>();

    private Set<String> res = new HashSet<String>();

    private ProgressDialog dialog;

    private int unreadMessages = -1;
    private boolean showMessagesMenuItem = false;

    public IOMenuNode(Questionnaire questionnaire, String nodeName) {
        super(questionnaire, nodeName);
    }

    @Override
    public void enter() {

        hideMenuButton();
        hideBackButton();

        getQuestionnaire().clearStack();

        Variable<?> user = questionnaire.getValuePool().get(Util.VARIABLE_USERNAME);
        if (null != user && null != user.getExpressionValue() && null != user.getExpressionValue().toString()
                && !Util.ADMINUSER_NAME.equalsIgnoreCase(user.getExpressionValue().toString())) {
            dialog = ProgressDialog.show(questionnaire.getActivity(), "Arbejder", "Vent venligst...", true);
            new ReminderTask(questionnaire).execute();
            new RetrieveRecipientsTask(questionnaire, this).execute();
        } else {
            buildView();
            super.enter();
        }
    }

    @Override
    public String toString() {
        return "IOMenuNode(\"" + getNodeName() + "\") -> \"" + nextNode.getNodeName() + "\"";
    }

    @Override
    public void sendError() {
        dialog.dismiss();
    }

    @Override
    public void end(String result) {
        Log.d(TAG, result);
        Messages messages = new Gson().fromJson(result, Messages.class);
        int numberOfUnreadMessages = messages.unread;

        boolean refreshGui = numberOfUnreadMessages != unreadMessages;
        if (refreshGui) {
            unreadMessages = numberOfUnreadMessages;
            buildView();
            createView();
        }
        dialog.dismiss();
    }

    private void buildView() {
        skemaer.clear();
        skemaer.put("Gennemfør måling", RunSkema.class.getName());

        Variable<?> user = questionnaire.getValuePool().get(Util.VARIABLE_USERNAME);
        if (null != user && null != user.getExpressionValue() && null != user.getExpressionValue().toString()
                && Util.ADMINUSER_NAME.equalsIgnoreCase(user.getExpressionValue().toString())) {
            if (!Util.isServerUrlLocked(getQuestionnaire())) {
                skemaer.put(MENU_TEXT_EDIT_SERVER_URL, SetServerIpSkema.class.getName());
            }
            skemaer.put(MENU_TEXT_SHOW_UPLOAD_DEBUG, SetShowUploadDebugNodeSkema.class.getName());
            skemaer.put(MENU_TEXT_SET_ALARM, SetAlarmSkema.class.getName());
            questionnaire.addVariable(new Variable<String>(Util.VARIABLE_REAL_NAME, Util.ADMINUSER_NAME));
        } else {
            if (!Util.isServerUrlLocked(getQuestionnaire())) {
                skemaer.remove(MENU_TEXT_EDIT_SERVER_URL);
            }
            skemaer.remove(MENU_TEXT_SHOW_UPLOAD_DEBUG);
            skemaer.remove(MENU_TEXT_SET_ALARM);

            if (showMessagesMenuItem) {
                MessageFormat fmt = new MessageFormat(
                        "Beskeder{0,choice,0#|1#' ('én ny besked')'|1<' ('{0,number,integer} nye')'}");

                skemaer.put(fmt.format(new Integer[] { unreadMessages }), MessageSkema.class.getName());
            }

            skemaer.put("Mine målinger", PatientMeasurementSkema.class.getName());
            skemaer.put("Skift adgangskode", ChangePasswordSkema.class.getName());
        }

        clearElements();
        TextViewElement tve = new TextViewElement(this);
        tve.setText("Menu");
        addElement(tve);

        ListViewElement<String> lve = new ListViewElement<String>(this);
        String[] vals = skemaer.keySet().toArray(new String[skemaer.size()]);
        lve.setValues(vals);
        String[] res = new String[skemaer.size()];
        int i = 0;
        for (String key : vals) {
            Log.d(TAG, skemaer.get(key) + " -> " + key);
            res[i++] = skemaer.get(key);
        }
        lve.setResults(res);
        lve.setVariable(menu);
        lve.setNextNode(nextNode);

        addElement(lve);
    }

    @Override
    public void setRecipients(String result) {
        if (null != result && !"".equals(result)) {
            MessageRecipient[] messageRecipients = new Gson().fromJson(result, MessageRecipient[].class);
            showMessagesMenuItem = messageRecipients != null && messageRecipients.length > 0;

            buildView();
            super.enter();

            if (showMessagesMenuItem) {
                new RetrieveMessageListTask(questionnaire, this).execute();
            } else {
                dialog.dismiss();
            }
        }
    }

    public void setNextNode(Node nextNode) {
        this.nextNode = nextNode;
    }

    public void setMenu(Variable<String> menu) {
        this.menu = menu;
    }

}
