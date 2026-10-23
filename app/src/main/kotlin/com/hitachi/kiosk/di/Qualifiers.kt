package com.hitachi.kiosk.di

import javax.inject.Qualifier

/** Dispatcher for disk and network work (conventions §6). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for CPU work such as pricing and the Budget Plan search. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** App-wide scope for work that outlives a screen: session, sync, payment polling and print jobs (§13.4). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
