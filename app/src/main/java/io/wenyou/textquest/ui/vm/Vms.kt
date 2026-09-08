package io.wenyou.textquest.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.wenyou.textquest.WenYouApp

/**
 * 依据 [WenYouApp] 容器构造 [ViewModel] 的工厂。
 * 借助 CreationExtras 拿到 Application，再用调用方提供的构造器创建对应 VM。
 */
object Vms {
    fun <VM : ViewModel> factory(build: (WenYouApp.AppContainer) -> VM): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WenYouApp?
                    ?: throw IllegalStateException("ViewModel 缺少 APPLICATION_KEY")
                @Suppress("UNCHECKED_CAST")
                return build(app.container) as T
            }

            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                throw IllegalStateException("请通过带 CreationExtras 的路径创建 ViewModel")
            }
        }
}
