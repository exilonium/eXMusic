package app.exmusic.exilonium.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.content.ContextCompat
import app.exmusic.core.ui.utils.isAtLeastAndroid6
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class ActionReceiver(private val base: String) : BroadcastReceiver() {
    @OptIn(ExperimentalAtomicApi::class)
    companion object {
        private val requestCode = AtomicInt(100)
        val nextCode = requestCode.fetchAndIncrement()
    }

    class Action internal constructor(
        val value: String,
        val icon: Icon?,
        val title: String?,
        val contentDescription: String?,
        internal val onReceive: (Context, Intent) -> Unit
    ) {
        context(context: Context)
        val pendingIntent: PendingIntent
            get() = PendingIntent.getBroadcast(
                /* context = */ context,
                /* requestCode = */ nextCode,
                /* intent = */ Intent(value).setPackage(context.packageName),
                /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0)
            )
    }

    private val mutableActions = hashMapOf<String, Action>()
    val all get() = mutableActions.toMap()

    val intentFilter
        get() = IntentFilter().apply {
            mutableActions.keys.forEach { addAction(it) }
        }

    internal fun action(
        icon: Icon? = null,
        title: String? = null,
        contentDescription: String? = null,
        onReceive: (Context, Intent) -> Unit
    ) = readOnlyProvider<ActionReceiver, Action> { thisRef, property ->
        val name = "$base.${property.name}"
        val action = Action(
            value = name,
            onReceive = onReceive,
            icon = icon,
            title = title,
            contentDescription = contentDescription
        )

        thisRef.mutableActions += name to action
        { _, _ -> action }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = mutableActions[intent.action]
        if (action == null) {
            Log.w("ActionReceiver", "ActionReceiver $this got invalid action ${intent.action} (intent=$intent)!")
            return
        }
        action.onReceive(context, intent)
    }

    context(context: Context)
    fun register(
        @ContextCompat.RegisterReceiverFlags
        flags: Int = ContextCompat.RECEIVER_NOT_EXPORTED
    ) {
        val filter = intentFilter

        Log.d("ActionReceiver", "Registering ${this@ActionReceiver} with filter $filter")

        ContextCompat.registerReceiver(
            /* context  = */ context,
            /* receiver = */ this@ActionReceiver,
            /* filter   = */ filter,
            /* flags    = */ flags
        )
    }
}

private inline fun <ThisRef, Return> readOnlyProvider(
    crossinline provide: (
        thisRef: ThisRef,
        property: KProperty<*>
    ) -> (thisRef: ThisRef, property: KProperty<*>) -> Return
) = PropertyDelegateProvider<ThisRef, ReadOnlyProperty<ThisRef, Return>> { thisRef, property ->
    val provider = provide(thisRef, property)
    ReadOnlyProperty { innerThisRef, innerProperty -> provider(innerThisRef, innerProperty) }
}
