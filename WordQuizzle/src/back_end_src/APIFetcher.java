package back_end_src;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

/**
 * {@link APIFetcher} si occupa di interrogare il servizio API per recuperare le
 * traduzioni relative ad una certa parola.
 */
public class APIFetcher implements Runnable {

    //L'url è già stato costruito, per il fetch di una precisa parola, da RequestManager.
    private final String url;
    private final ArrayList<String> translations;

    public APIFetcher(String url, ArrayList<String> translations) {
        this.url = url;
        this.translations = translations;
    }

    @Override
    public void run() {
        URL url;
        try {
            //Se qualche altro APIFetcher ha già notificato un errore, non stiamo neanche a provarci, probabilmente
            //anche noi incontreremo lo stesso errore.
            if (!RequestManager.API_ERROR) {
                url = new URL(this.url);
                URLConnection urlConnection = url.openConnection();
                HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
                if (httpURLConnection.getResponseCode() > 299) {
                    //In caso di errore, notifichiamolo settando un flag apposito.
                    RequestManager.API_ERROR = true;
                } else {
                    BufferedReader in = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                    StringBuilder jsonResponse = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        jsonResponse.append(line);
                    }
                    JsonObject obj = (JsonObject) JsonParser.parseString(String.valueOf(jsonResponse));
                    JsonArray matches = (JsonArray) obj.get("matches");

                    //Recuperiamo le traduzioni e le inseriamo nell'ArrayList associato a questo APIFetcher.
                    for (JsonElement match : matches
                    ) {
                        String word = ((JsonObject) match).get("translation").getAsString().toLowerCase();
                        if (!translations.contains(word)) translations.add(word);
                    }

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "APIFetcher{" +
                "url='" + url + '\'' +
                '}';
    }
}
