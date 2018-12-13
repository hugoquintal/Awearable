package com.example.administrator.awearable.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.administrator.awearable.model.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * class that will be used as a bridge between the DBOpenHelper and AnalogWatchService
 */

public class TableDataSource {

    private static final String LOGTAG = "test - db - TDS";

    SQLiteOpenHelper dbHelper2;
    SQLiteDatabase database2;


    private static final String[] allColumns = {
            DBOpenHelper.COLUMN_ID,
            DBOpenHelper.COLUMN_ACTION,
            DBOpenHelper.COLUMN_TIME,
            DBOpenHelper.COLUMN_UPDATED,
            DBOpenHelper.COLUMN_PEEKCARD,
            DBOpenHelper.COLUMN_OTHERAPPS};

    public TableDataSource(Context context) {
        dbHelper2 = new DBOpenHelper(context);
    }

    //open db
    public void open() {
        Log.i(LOGTAG, "DATABASE OPENED");
        database2 = dbHelper2.getWritableDatabase();
    }

    //close db
    public void close() {
        Log.i(LOGTAG, "DATABASE CLOSED");
        dbHelper2.close();
    }

    //delete "X" table content
    public void deleteTableData(String table) {
        if (table.equals("action")) {
            database2.execSQL("delete from " + DBOpenHelper.TABLE_ONE);
            Log.i(LOGTAG, "TABLE ACTION DELETED");
        }
    }


    //---------------------------------------------------ACTIONS

    //create a new action row
    public Action createAction(Action action){
        ContentValues values = new ContentValues();
        values.put(DBOpenHelper.COLUMN_ACTION, action.getAction());
        values.put(DBOpenHelper.COLUMN_TIME, action.getTime());
        values.put(DBOpenHelper.COLUMN_UPDATED, action.getSet());
        values.put(DBOpenHelper.COLUMN_OTHERAPPS, action.getAccessOtherApps());
        long insertId = database2.insert(DBOpenHelper.TABLE_ONE, null, values);
        action.setId(insertId);
        return action;
    }

    //update a action row
    public void updateAction(long id){
        ContentValues newValues = new ContentValues();
        newValues.put(DBOpenHelper.COLUMN_UPDATED, 1L);
        database2.update(DBOpenHelper.TABLE_ONE, newValues, DBOpenHelper.COLUMN_ID + " = " + id, null);
        //Log.i(LOGTAG, "ACTION UPDATED");
    }

    //get all actions stored on the device
    public List<Action> findAllActions(){
        List<Action> actions = new ArrayList<Action>();
        Cursor cursor = database2.query(DBOpenHelper.TABLE_ONE, allColumns, null, null, null, null ,null);
        //Log.i(LOGTAG, "Returned" + cursor.getCount() + "rows");
        if(cursor.getCount()>0){
            while(cursor.moveToNext()){
                Action information = new Action();
                information.setId(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_ID)));
                information.setAction(cursor.getString(cursor.getColumnIndex(DBOpenHelper.COLUMN_ACTION)));
                information.setTime(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_TIME)));
                information.setSet(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_UPDATED)));
                information.setAccessOtherApps(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_OTHERAPPS)));
                actions.add(information);
            }
        }
        return actions;
    }

    //find all action that have the a certain value on the updated field (updated = 1 if the action already sent to external db)
    public List<Action> findAllActionsSet(long set){
        List<Action> actions = new ArrayList<Action>();
        Cursor cursor = database2.query(DBOpenHelper.TABLE_ONE, allColumns, "updated= " + set, null, null, null, null, "400");//400
        //Log.i(LOGTAG, "Returned" + cursor.getCount() + "rows");
        if(cursor.getCount()>0){
            while(cursor.moveToNext()){
                Action information = new Action();
                information.setId(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_ID)));
                information.setAction(cursor.getString(cursor.getColumnIndex(DBOpenHelper.COLUMN_ACTION)));
                information.setTime(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_TIME)));
                information.setSet(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_UPDATED)));
                information.setAccessOtherApps(cursor.getLong(cursor.getColumnIndex(DBOpenHelper.COLUMN_OTHERAPPS)));
                actions.add(information);
            }
            cursor.close();
        }
        return actions;
    }


}
