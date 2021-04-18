/*
 * Copyright (C) 2016-2021 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.ui.screens.habits.list

import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.Timestamp
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.tasks.ExportCSVTask
import org.isoron.uhabits.core.tasks.TaskRunner
import org.isoron.uhabits.core.ui.callbacks.OnConfirmedCallback
import org.isoron.uhabits.core.utils.DateUtils.Companion.getToday
import java.io.File
import java.io.IOException
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.roundToInt

open class ListHabitsBehavior @Inject constructor(
    private val habitList: HabitList,
    private val dirFinder: DirFinder,
    private val taskRunner: TaskRunner,
    private val screen: Screen,
    private val commandRunner: CommandRunner,
    private val prefs: Preferences,
    private val bugReporter: BugReporter
) {
    fun onClickHabit(h: Habit) {
        screen.showHabitScreen(h)
    }

    fun onEdit(habit: Habit, timestamp: Timestamp?) {
        val entries = habit.computedEntries
        val oldValue = entries.get(timestamp!!).value.toDouble()
        screen.showNumberPicker(
            oldValue / 1000,
            habit.unit
        ) { newValue: Double ->
            val value = (newValue * 1000).roundToInt()
            commandRunner.run(CreateRepetitionCommand(habitList, habit, timestamp, value))
        }
    }

    fun onExportCSV() {
        val selected: MutableList<Habit> = LinkedList()
        for (h in habitList) selected.add(h)
        val outputDir = dirFinder.getCSVOutputDir()
        taskRunner.execute(
            ExportCSVTask(habitList, selected, outputDir) { filename: String? ->
                if (filename != null) screen.showSendFileScreen(filename) else screen.showMessage(
                    Message.COULD_NOT_EXPORT
                )
            }
        )
    }

    fun onFirstRun() {
        prefs.isFirstRun = false
        prefs.updateLastHint(-1, getToday())
        screen.showIntroScreen()
    }

    fun onReorderHabit(from: Habit, to: Habit) {
        taskRunner.execute { habitList.reorder(from, to) }
    }

    fun onRepairDB() {
        taskRunner.execute {
            habitList.repair()
            screen.showMessage(Message.DATABASE_REPAIRED)
        }
    }

    fun onSendBugReport() {
        bugReporter.dumpBugReportToFile()
        try {
            val log = bugReporter.getBugReport()
            screen.showSendBugReportToDeveloperScreen(log)
        } catch (e: IOException) {
            e.printStackTrace()
            screen.showMessage(Message.COULD_NOT_GENERATE_BUG_REPORT)
        }
    }

    fun onStartup() {
        prefs.incrementLaunchCount()
        if (prefs.isFirstRun) onFirstRun()
    }

    fun onToggle(habit: Habit, timestamp: Timestamp?, value: Int) {
        commandRunner.run(
            CreateRepetitionCommand(habitList, habit, timestamp!!, value)
        )
    }

    fun onSyncKeyOffer(syncKey: String, encryptionKey: String) {
        if (prefs.syncKey == syncKey) {
            screen.showMessage(Message.SYNC_KEY_ALREADY_INSTALLED)
            return
        }
        screen.showConfirmInstallSyncKey {
            prefs.enableSync(syncKey, encryptionKey)
            screen.showMessage(Message.SYNC_ENABLED)
        }
    }

    enum class Message {
        COULD_NOT_EXPORT,
        IMPORT_SUCCESSFUL,
        IMPORT_FAILED,
        DATABASE_REPAIRED,
        COULD_NOT_GENERATE_BUG_REPORT,
        FILE_NOT_RECOGNIZED,
        SYNC_ENABLED,
        SYNC_KEY_ALREADY_INSTALLED
    }

    interface BugReporter {
        fun dumpBugReportToFile()

        @Throws(IOException::class)
        fun getBugReport(): String
    }

    interface DirFinder {
        fun getCSVOutputDir(): File
    }

    fun interface NumberPickerCallback {
        fun onNumberPicked(newValue: Double)
        fun onNumberPickerDismissed() {}
    }

    interface Screen {
        fun showHabitScreen(h: Habit)
        fun showIntroScreen()
        fun showMessage(m: Message)
        fun showNumberPicker(
            value: Double,
            unit: String,
            callback: NumberPickerCallback
        )

        fun showSendBugReportToDeveloperScreen(log: String)
        fun showSendFileScreen(filename: String)
        fun showConfirmInstallSyncKey(callback: OnConfirmedCallback)
    }
}
