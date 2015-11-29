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
