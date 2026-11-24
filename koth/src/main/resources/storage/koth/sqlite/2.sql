-- The start of the last window that was skipped or stopped, so a restart doesn't run it after all. One row.
CREATE TABLE handled_window (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    start TEXT NOT NULL
);
