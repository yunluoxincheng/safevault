package com.ttt.safevault.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.model.BackendService;

/**
 * ViewModel工厂类
 * 用于创建带有依赖的ViewModel实例
 */
public class ViewModelFactory extends ViewModelProvider.AndroidViewModelFactory {

    private final Application application;
    private final BackendService backendService;

    public ViewModelFactory(@NonNull Application application) {
        super(application);
        this.application = application;
        this.backendService = ServiceLocator.getInstance().getBackendService();
    }

    public ViewModelFactory(@NonNull Application application, BackendService backendService) {
        super(application);
        this.application = application;
        this.backendService = backendService;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LoginViewModel.class)) {
            return (T) new LoginViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(PasswordListViewModel.class)) {
            return (T) new PasswordListViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(PasswordDetailViewModel.class)) {
            return (T) new PasswordDetailViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(EditPasswordViewModel.class)) {
            return (T) new EditPasswordViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(GeneratorViewModel.class)) {
            return (T) new GeneratorViewModel(application);
        }
        if (modelClass.isAssignableFrom(ShareViewModel.class)) {
            return (T) new ShareViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(ReceiveShareViewModel.class)) {
            return (T) new ReceiveShareViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(ShareHistoryViewModel.class)) {
            return (T) new ShareHistoryViewModel(application, backendService);
        }
        if (modelClass.isAssignableFrom(AuthViewModel.class)) {
            return (T) new AuthViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
