# RxSerach

https://medium.com/@diolor/improving-ux-with-rxjava-4440a13b157f#.qdhu122d1


# 使用RxJava 提升用户体验

> 原文链接：https://medium.com/@diolor/improving-ux-with-rxjava-4440a13b157f#.qdhu122d1</br>
> 翻译： [hanks](https://github.com/hanks-zyh)

“网络永连接，服务器不出错，培根没肥肉”

![Search list animation by Daley P Maasz from Dribbble.](http://file.bmob.cn/M02/C6/EF/oYYBAFZbFyCADEAkAAV75iu06QU497.gif)

友好的用户体验通常是用户很爽，但开发者很痛苦。 当用户点击一个按钮后，因为后端没有及时响应而卡住界面，这回让用户很失望。
现在让我们创建一个更好的搜索功能（当用户在EditText输入文字时进行搜索）：
 - 尽可能少的网络请求
 - 尽可能少的错误信息
 Rx 的逻辑会十分简单并且针对小的细节

让我们从基本的逻辑开始:
让用户输入文字时执行网络请求，当结果返回时进行显示

```
RxTextView.textChanges(searchEditText)
     .flatMap(Api::searchItems)
     .subscribe(this::updateList, t->showError());
```

## 1. 减少网络请求
有两个问题：
 - 1 . 输入的文字每变化一个就执行请求（很垃圾），例如： 用户很快的输入 “a”（搜索”a“），然后”b”（搜索“ab”），然后“c”（搜索”abc”），然后又删除”c”（搜索“ab”）， 输入“e”（搜索”abe”）。这个过程执行了5次请求。
 - 2： 假如网络情况很差，多个线程同时进行，这时候就可能发生错误：如当用户输入“a”，然后”ab”，但是搜索“ab”的结果先返回了，之后返回了搜索“a的结果”，这时候输入框的文字是”ab”，但结果却是搜索“a”的
解决方案：
1. 增加 throttling （节流）行为
`debounce()` 的作用正是如此， 时间为 100-150ms 为佳，如果服务器返回需要300ms，那么你可以在 500ms时更新UI界面

```

RxTextView.textChanges(searchEditText)
     .debounce(150, MILLISECONDS)
     .flatMap(Api::searchItems)
     .subscribe(this::updateList, t->showError());
```

2. 取消之前的请求:
使用 `switchMap`来替代  `flatMap`， `switchMap`停止之前发出的请求， 假如在150ms的时候搜索”ab“，在300ms的时候搜索了”abcd“，但是搜索”ab”的请求需要花费超过150ms，那么搜索“abcd”的请求开始的时候将会取消上一个请求，用户只会获取到最后的一次搜索的结果。

```

RxTextView.textChanges(searchEditText)
     .debounce(150, MILLISECONDS)
     .switchMap(Api::searchItems)
     .subscribe(this::updateList, t->showError());
```

## 2. 没有错误提示/没有网络错误提示
如果网络请求失败，将不再观测文本的变化，（因为调用了OnError，整个事件流中断），这可以通过添加错误捕获函数轻松搞定

```

RxTextView.textChanges(searchEditText)
     .debounce(150, MILLISECONDS)
     .switchMap(Api::searchItems)
     .onErrorResumeNext(t-> empty())
     .subscribe(this::updateList);
```

但是不要这样做， 我们需要更好的处理方案。假如   `searchItems()` api因为网络连接而返回失败的呢？ 或者是因为偶然的连接失败呢？
我们需要 **重试机制** :

```

RxTextView.textChanges(searchEditText)
     .debounce(150, MILLISECONDS)
     .switchMap(Api::searchItems)
     .retryWhen(new RetryWithConnectivity())
     .subscribe(this::updateList, t->showError());
```

如何进一步提升呢？ 通过增加超时时间。 因为我们的 设计师 Lenzing 说 ”即使1s对用户来说也很漫长”，于是我们有做了下面的事情：

```


RxTextView.textChanges(searchEditText)
     .debounce(150, MILLISECONDS)
     .switchMap(Api::searchItems)
     .retryWhen(new RetryWithConnectivityIncremental(context, 5, 15, SECONDS))
     .subscribe(this::updateList, t->showErrorToUser());
```

对比 ` RetryWithConnectivityIncremental`  和 `RetryWithConnectivity` ， `RetryWithConnectivityIncremental` 更智能，它首先设置了 5 秒的超时时间， 如果这个时候连接失败，如果用户再次重试的话，超时时间会被设置的更长（如上面的15 秒）。

现在，使用RxJava 提示了用户体验， 请求防抖动， 取消上次请求获取最新的结果， 智能的网络超时重试。这些可能用户不会注意到，但是这是一个好的设计 ;)

标签： **AndroidRxjavaUX**

完整代码：

```

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
                .debounce(600, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
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
//                .retryWhen(new RetryWithConnectivityIncremental(MainActivity.this, 5, 15, TimeUnit.MILLISECONDS))
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
                /*.onErrorResumeNext(new Func1<Throwable, Observable<? extends String>>() {
                    @Override public Observable<? extends String> call(Throwable throwable) {
                        return Observable.just("error result");
                    }
                })*/

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

```

BroadcastObservable.java
```
package com.hanks.rxsearch;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;
/**
 * Created by hanks on 15-11-29.
 */
public class BroadcastObservable implements Observable.OnSubscribe<Boolean> {

    private final Context context;

    public BroadcastObservable(Context context) {
        this.context = context;
    }

    public static Observable<Boolean> fromConnectivityManager(Context context) {
        return Observable.create(new BroadcastObservable(context)).share();
    }

    private static Subscription unsubscribeInUiThread(final Action0 unsubscribe) {
        return Subscriptions.create(new Action0() {
            @Override public void call() {
                if (Looper.getMainLooper() == Looper.myLooper()) {
                    unsubscribe.call();
                } else {
                    final Scheduler.Worker inner = AndroidSchedulers.mainThread().createWorker();
                    inner.schedule(new Action0() {
                        @Override public void call() {
                            unsubscribe.call();
                            inner.unsubscribe();
                        }
                    });
                }
            }
        });
    }

    @Override public void call(final Subscriber<? super Boolean> subscriber) {

        final BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override public void onReceive(Context context, Intent intent) {
                subscriber.onNext(isConnectedToInternet());
            }
        };

        context.registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        subscriber.add(unsubscribeInUiThread(new Action0() {
            @Override public void call() {
                context.unregisterReceiver(receiver);
            }
        }));

    }

    private Boolean isConnectedToInternet() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

}


```



RetryWithConnectivityIncremental.java
```
package com.hanks.rxsearch;
import android.content.Context;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import retrofit.RetrofitError;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
/**
 * RetryWithConnectivityIncremental
 * <p/>
 * Created by hanks on 15-11-29.
 */
public class RetryWithConnectivityIncremental implements Func1<Observable<? extends Throwable>, Observable<?>> {

    private final int startTimeOut;
    private final int maxTimeOut;

    private final TimeUnit timeUnit;

    private int timeOut;

    private Observable<Boolean> isConnected;

    public RetryWithConnectivityIncremental(Context context, int startTimeOut, int maxTimeOut, TimeUnit timeUnit) {
        this.startTimeOut = startTimeOut;
        this.maxTimeOut = maxTimeOut;
        this.timeOut = startTimeOut;
        this.timeUnit = timeUnit;
        isConnected = getConnectedObservable(context);
    }

    private Observable<Boolean> getConnectedObservable(Context context) {
        return BroadcastObservable.fromConnectivityManager(context)
                .distinctUntilChanged()
                .filter(new Func1<Boolean, Boolean>() {
                    @Override public Boolean call(Boolean isConnected) {
                        return isConnected;
                    }
                });
    }

    private Observable.Transformer<Boolean, Boolean> attachIncementalTimeOut() {

        return new Observable.Transformer<Boolean, Boolean>() {

            @Override public Observable<Boolean> call(Observable<Boolean> observable) {
                return observable.timeout(timeOut, timeUnit).doOnError(new Action1<Throwable>() {

                    @Override public void call(Throwable throwable) {
                        if (throwable instanceof TimeoutException) {
                            timeOut = timeOut > maxTimeOut ? maxTimeOut : timeOut + startTimeOut;
                        }
                    }

                });
            }
        };
    }

    @Override public Observable<?> call(final Observable<? extends Throwable> observable) {
        return observable.flatMap(new Func1<Throwable, Observable<Boolean>>() {
            @Override public Observable<Boolean> call(Throwable throwable) {
                if (throwable instanceof RetrofitError && ((RetrofitError) throwable).getKind() == RetrofitError.Kind.NETWORK) {
                    return isConnected;
                } else {
                    return Observable.error(throwable);
                }
            }
        }).compose(attachIncementalTimeOut());
    }
}

```
