package com.u9porn.ui.user;

import android.arch.lifecycle.Lifecycle;
import android.support.annotation.NonNull;

import com.hannesdorfmann.mosby3.mvp.MvpBasePresenter;
import com.trello.rxlifecycle2.LifecycleProvider;
import com.u9porn.data.DataManager;
import com.u9porn.data.model.User;
import com.u9porn.rxjava.CallBackWrapper;
import com.u9porn.rxjava.RxSchedulersHelper;

import javax.inject.Inject;

import io.reactivex.disposables.Disposable;

/**
 * 用户登录
 *
 * @author flymegoc
 * @date 2017/12/10
 */

public class UserPresenter extends MvpBasePresenter<UserView> implements IUser {

    private LifecycleProvider<Lifecycle.Event> provider;
    private DataManager dataManager;
    private User user;

    @Inject
    public UserPresenter(LifecycleProvider<Lifecycle.Event> provider, DataManager dataManager, User user) {
        this.provider = provider;
        this.dataManager = dataManager;
        this.user = user;
    }

    @Override
    public void login(String username, String password, String captcha) {
        login(username, password, captcha, null);
    }

    public void login(String username, String password, String captcha, final LoginListener loginListener) {
        dataManager.userLoginPorn9Video(username, password, captcha)
                .compose(RxSchedulersHelper.<User>ioMainThread())
                .compose(provider.<User>bindUntilEvent(Lifecycle.Event.ON_DESTROY))
                .subscribe(new CallBackWrapper<User>() {
                    @Override
                    public void onBegin(Disposable d) {
                        ifViewAttached(new ViewAction<UserView>() {
                            @Override
                            public void run(@NonNull UserView view) {
                                if (loginListener == null) {
                                    view.showLoading(true);
                                }
                            }
                        });
                    }

                    @Override
                    public void onSuccess(final User user) {
                        user.copyProperties(UserPresenter.this.user);
                        if (loginListener != null) {
                            loginListener.loginSuccess(user);
                        } else {
                            ifViewAttached(new ViewAction<UserView>() {
                                @Override
                                public void run(@NonNull UserView view) {
                                    view.showContent();
                                    view.loginSuccess(user);
                                }
                            });
                        }

                    }

                    @Override
                    public void onError(final String msg, int code) {
                        if (loginListener != null) {
                            loginListener.loginFailure(msg);
                        } else {
                            ifViewAttached(new ViewAction<UserView>() {
                                @Override
                                public void run(@NonNull UserView view) {
                                    view.showContent();
                                    view.loginError(msg);
                                }
                            });
                        }
                    }
                });
    }

    @Override
    public void register(String username, String password1, String password2, String email, String captchaInput) {
        dataManager.userRegisterPorn9Video(username, password1, password2, email, captchaInput)
                .compose(RxSchedulersHelper.<User>ioMainThread())
                .compose(provider.<User>bindUntilEvent(Lifecycle.Event.ON_DESTROY))
                .subscribe(new CallBackWrapper<User>() {
                    @Override
                    public void onBegin(Disposable d) {
                        ifViewAttached(new ViewAction<UserView>() {
                            @Override
                            public void run(@NonNull UserView view) {
                                view.showLoading(true);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(final User user) {
                        ifViewAttached(new ViewAction<UserView>() {
                            @Override
                            public void run(@NonNull UserView view) {
                                user.copyProperties(UserPresenter.this.user);
                                view.showContent();
                                view.registerSuccess(user);
                            }
                        });
                    }

                    @Override
                    public void onError(final String msg, int code) {
                        ifViewAttached(new ViewAction<UserView>() {
                            @Override
                            public void run(@NonNull UserView view) {
                                view.showContent();
                                view.registerFailure(msg);
                            }
                        });
                    }
                });
    }

    public interface LoginListener {
        void loginSuccess(User user);

        void loginFailure(String message);
    }
}
