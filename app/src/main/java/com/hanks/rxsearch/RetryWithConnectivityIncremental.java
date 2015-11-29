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
