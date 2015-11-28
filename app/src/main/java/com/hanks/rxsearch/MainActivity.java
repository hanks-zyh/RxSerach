package com.hanks.rxsearch;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.jakewharton.rxbinding.widget.RxTextView;

import butterknife.Bind;
import retrofit.RestAdapter;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    @Bind(R.id.et_keyword) EditText et_keyword;
    @Bind(R.id.tv_result)  TextView tv_result;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RestAdapter retrofit = new RestAdapter.Builder().setEndpoint("https://suggest.taobao.com")
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .build();

        final SearchService service = retrofit.create(SearchService.class);

        RxTextView.textChanges(et_keyword)
                .subscribeOn(Schedulers.io())
                .flatMap(new Func1<CharSequence, rx.Observable<String>>() {
                    @Override public rx.Observable<String> call(CharSequence keyword) {
                        return service.searchProdcut("utf-8", (String) keyword);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override public void call(String s) {
                        Log.d("result", s);
                        showpop(s);
                    }
                }, new Action1<Throwable>() {
                    @Override public void call(Throwable throwable) {
                        Log.d("error", throwable.getMessage());
                    }
                });

    }

    private void showpop(String result) {
        tv_result.setText(result);
    }

    interface SearchService {

        @GET("/sug") rx.Observable<String> searchProdcut(@Query("code") String code, @Query("q") String keyword);
    }

    //    https://suggest.taobao.com/sug?code=utf-8&q=%E6%89%8B%E6%9C%BA
}
