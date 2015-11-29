package com.hanks.rxsearch;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.jakewharton.rxbinding.widget.RxTextView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;
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
        ButterKnife.bind(this);

        RestAdapter retrofit = new RestAdapter.Builder().setEndpoint("https://suggest.taobao.com")
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setConverter(new GsonConverter(new Gson()))
                .build();

        final SearchService service = retrofit.create(SearchService.class);

        RxTextView.textChanges(et_keyword)
                // 上面的对 tv_result 的操作需要在主线程
                .subscribeOn(AndroidSchedulers.mainThread())
                .debounce(1000, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .filter(new Func1<CharSequence, Boolean>() {
                    @Override public Boolean call(CharSequence charSequence) {
                        // 清空搜索出来的结构
                        tv_result.setText("");
                        //当 EditText 中文字大于0的时候
                        return charSequence.length() > 0;
                    }
                })
                .switchMap(new Func1<CharSequence, Observable<Data>>() {
                    @Override public Observable<Data> call(CharSequence charSequence) {
                        // 搜索
                        return service.searchProdcut("utf-8", charSequence.toString());
                    }
                })
                // 网络操作在io线程
                .subscribeOn(Schedulers.io())
                //将 data 转换成 ArrayList<ArrayList<String>>
                .map(new Func1<Data, ArrayList<ArrayList<String>>>() {
                    @Override public ArrayList<ArrayList<String>> call(Data data) {
                        return data.result;
                    }
                })
                // 将 ArrayList<ArrayList<String>> 中每一项提取出来 ArrayList<String>
                .flatMap(new Func1<ArrayList<ArrayList<String>>, Observable<ArrayList<String>>>() {
                    @Override public Observable<ArrayList<String>> call(ArrayList<ArrayList<String>> arrayLists) {
                        return Observable.from(arrayLists);
                    }
                })
                .filter(new Func1<ArrayList<String>, Boolean>() {
                    @Override public Boolean call(ArrayList<String> strings) {
                        return strings.size() >= 2;
                    }
                })
                .map(new Func1<ArrayList<String>, String>() {
                    @Override public String call(ArrayList<String> strings) {
                        return "[商品名称:" + strings.get(0) + ", ID:" + strings.get(1) + "]\n";
                    }
                })
                // 发生错误后不要调用 onError，而是转到 onErrorResumeNext
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends String>>() {
                    @Override public Observable<? extends String> call(Throwable throwable) {
                        return Observable.just("error result");
                    }
                })
                //
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override public void call(String charSequence) {
                        showpop(charSequence);
                    }
                }, new Action1<Throwable>() {
                    @Override public void call(Throwable throwable) {
                        Toast.makeText(MainActivity.this, "error", Toast.LENGTH_SHORT).show();
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
