package com.mappo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mappo.data.db.steam.ActionLayerDao
import com.mappo.data.db.steam.ActionSetDao
import com.mappo.data.db.steam.ActivatorDao
import com.mappo.data.db.steam.BindingDao
import com.mappo.data.db.steam.BindingGroupDao
import com.mappo.data.db.steam.ControllerProfileDao
import com.mappo.data.db.steam.GameActionDao
import com.mappo.data.db.steam.GroupInputDao
import com.mappo.data.db.steam.LayerPresetBindingDao
import com.mappo.data.db.steam.PresetBindingDao
import com.mappo.data.db.steam.SourceModeShiftDao
import com.mappo.data.db.steam.SteamTypeConverters
import com.mappo.data.model.AppLayoutBinding
import com.mappo.data.model.KeyLayout
import com.mappo.data.model.KeyboardTemplate
import com.mappo.data.model.OverlayElement
import com.mappo.data.model.Layout
import com.mappo.data.model.steam.ActionLayer
import com.mappo.data.model.steam.ActionSet
import com.mappo.data.model.steam.Activator
import com.mappo.data.model.steam.Binding
import com.mappo.data.model.steam.BindingGroup
import com.mappo.data.model.steam.ControllerProfile
import com.mappo.data.model.steam.GameAction
import com.mappo.data.model.steam.GroupInput
import com.mappo.data.model.steam.LayerPresetBinding
import com.mappo.data.model.steam.PresetBinding
import com.mappo.data.model.steam.SourceModeShift

@Database(
    entities = [
        KeyLayout::class,
        Layout::class,
        AppLayoutBinding::class,
        KeyboardTemplate::class,
        OverlayElement::class,
        ControllerProfile::class,
        ActionSet::class,
        ActionLayer::class,
        GameAction::class,
        BindingGroup::class,
        GroupInput::class,
        Activator::class,
        Binding::class,
        PresetBinding::class,
        LayerPresetBinding::class,
        SourceModeShift::class,
    ],
    version = 22,
    exportSchema = false
)
@TypeConverters(SteamTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun keyLayoutDao(): KeyLayoutDao
    abstract fun layoutDao(): LayoutDao
    abstract fun appLayoutBindingDao(): AppLayoutBindingDao
    abstract fun keyboardTemplateDao(): KeyboardTemplateDao
    abstract fun overlayElementDao(): OverlayElementDao

    abstract fun controllerProfileDao(): ControllerProfileDao
    abstract fun actionSetDao(): ActionSetDao
    abstract fun actionLayerDao(): ActionLayerDao
    abstract fun gameActionDao(): GameActionDao
    abstract fun bindingGroupDao(): BindingGroupDao
    abstract fun groupInputDao(): GroupInputDao
    abstract fun activatorDao(): ActivatorDao
    abstract fun bindingDao(): BindingDao
    abstract fun presetBindingDao(): PresetBindingDao
    abstract fun layerPresetBindingDao(): LayerPresetBindingDao
    abstract fun sourceModeShiftDao(): SourceModeShiftDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mappo.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
