<!--
SPDX-FileCopyrightText: 2024-2025 Daniel Getz <dan@getzit.net>

SPDX-License-Identifier: GPL-3.0-or-later
-->

# Repti

[Repti](https://github.com/talflon/repti-android) is a simple Android app
to keep track of things to do regularly.

For example, "exercise", "call Mom", "study math", "practice music",
"check to-do list"—intended for course-grained reminders of things that are good to do.
The last day that a task was done is stored, and presented to the user
as "X days ago". The user can then update when tasks were last done.

It's written in Kotlin using Jetpack Compose
and released under the [GPL Version 3 (or later)](LICENSES/GPL-3.0-or-later.txt).

## Version 0.1, 2025-09-02: implemented features

- Editable list of named tasks, scrolling, reordering by dragging
- Mark done by swiping right
- Set done date more precisely, by tapping to bring up a modal with date pickers
- Save and load from a file, for backups

## Project goals

### Core goals

- Remind and encourage to do the tasks
- Remind and encourage to do tasks that have been left undone for longer
- Avoid distracting or demotivating the user: see non-goals

### Stretch goals

- Undo/redo
- Group tasks into categories, to track both the coarse-grained
  outer category and the finer-grained specific task
- Automatic sync between devices
- Encrypt user data
- Attach notes to tasks

### Non-goals

- Keep a permanent log: could distract a user into spending time on
  correcting the past log for accuracy. The less data is stored, the less
  data there is to input. If you're not sure when something was last done,
  just mark it as not done. Doing is the important part, not recording.
- Push a particular task, or time to do a task, on the user: while this
  kind of thing might motivate some users, it could demotivate others.
  Users should use other apps for scheduling and to-do lists.