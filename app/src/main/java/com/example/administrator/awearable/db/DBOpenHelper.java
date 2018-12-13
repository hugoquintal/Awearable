package com.example.administrator.awearable.db;

import android.database.sqlite.SQLiteOpenHelper;

import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Created by Administrator on 9/04/2018.
 */

public class DBOpenHelper extends SQLiteOpenHelper {
        // If you change the database schema, you must increment the database version.
        private static final String LOGTAG = "test - DB";
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "basededadosana.db";

        public static final String TABLE_ONE = "one";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_ACTION = "action";
        public static final String COLUMN_TIME = "timestamp";
        public static final String COLUMN_UPDATED = "updated";
        public static final String COLUMN_PEEKCARD = "peekcard";
        public static final String COLUMN_OTHERAPPS = "otherapps";


    private static final String TABLE_CREATE_ONE = "CREATE TABLE " + TABLE_ONE + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_ACTION + " TEXT, " +
            COLUMN_TIME + " NUMERIC, " +
            COLUMN_UPDATED + " NUMERIC, " +
            COLUMN_PEEKCARD + " NUMERIC, " +
            COLUMN_OTHERAPPS + " NUMERIC " +
            ")";


    public DBOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        //db.execSQL(SQL_CREATE_ENTRIES);
        //db.execSQL("DROP TABLE IF EXISTS " + TABLE_INFO);
        db.execSQL(TABLE_CREATE_ONE);
        //db.execSQL(TABLE_CREATE_TWO);
        //db.execSQL(TABLE_CREATE_THREE);
        //db.execSQL(TABLE_CREATE_FOUR);
        Log.i(LOGTAG, "TABLES CREATED");
    }

    //called every time the database version is changed and that is done manualy every time the structure of the db is changed
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is to simply to discard the data and start over
        //db.execSQL(SQL_DELETE_ENTRIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ONE);
        //db.execSQL("DROP TABLE IF EXISTS " + TABLE_TWO);
        //db.execSQL("DROP TABLE IF EXISTS " + TABLE_THREE);
        //db.execSQL("DROP TABLE IF EXISTS " + TABLE_FOUR);
        //db.execSQL("DROP TABLE IF EXISTS " + TABLE_FIVE);
        Log.i(LOGTAG, "TABLES DROPPED");
        onCreate(db);
    }

}
