package com.hanks.rxsearch;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;
import com.jakewharton.rxbinding.widget.RxTextView;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

public class MainActivity extends AppCompatActivity {

    @Bind(R.id.et_keyword) EditText et_keyword;
    @Bind(R.id.tv_result)  TextView tv_result;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        RestAdapter retrofit = new RestAdapter.Builder().setEndpoint("https://suggest.taobao.com")
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setConverter(new GsonConverter(new Gson()))
                .build();

        final SearchService service = retrofit.create(SearchService.class);

        RxTextView.textChanges(et_keyword)
                .filter(new Func1<CharSequence, Boolean>() {
                    @Override public Boolean call(CharSequence charSequence) {
                        return charSequence.length() > 0;
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .flatMap(new Func1<CharSequence, Observable<Data>>() {
                    @Override public Observable<Data> call(CharSequence charSequence) {
                        return service.searchProdcut("utf-8", charSequence.toString());
                    }
                })
                .doOnSubscribe(new Action0() {
                    @Override public void call() {
                        Log.i("doOnSubscribe","clear");
                        tv_result.setText("");
                    }
                })
                .map(new Func1<Data, ArrayList<ArrayList<String>>>() {
                    @Override public ArrayList<ArrayList<String>> call(Data data) {
                        return data.result;
                    }
                })
                .flatMap(new Func1<ArrayList<ArrayList<String>>, Observable<ArrayList<String>>>() {
                    @Override public Observable<ArrayList<String>> call(ArrayList<ArrayList<String>> arrayLists) {
                        return Observable.from( arrayLists);
                    }
                })
                .flatMap(new Func1<ArrayList<String>, Observable<String>>() {
                    @Override public Observable<String> call(ArrayList<String> strings) {
                        return Observable.from(strings);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override public void call(String charSequence) {
                        showpop(charSequence);
                    }
                });

    }

    private void showpop(String result) {
        tv_result.append(result);
    }

    interface SearchService {

        @GET("/sug") Observable<Data> searchProdcut(@Query("code") String code, @Query("q") String keyword);
    }

    class Data {
        public ArrayList<ArrayList<String>> result;
    }

    //    https://suggest.taobao.com/sug?code=utf-8&q=%E6%89%8B%E6%9C%BA
}
