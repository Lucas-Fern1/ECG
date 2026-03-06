package com.example.ecg;

import android.util.Base64;

import java.io.*;
import java.util.ArrayList;

public class SerializationHelper {

    public static String serialize(ArrayList<ArrhythmiaEvent> list) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(list);
        oos.close();
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<ArrhythmiaEvent> deserialize(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        ArrayList<ArrhythmiaEvent> list = (ArrayList<ArrhythmiaEvent>) ois.readObject();
        ois.close();
        return list;
    }
}