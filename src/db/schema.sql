DROP TABLE IF EXISTS track_history CASCADE;
DROP TABLE IF EXISTS track_artist CASCADE;
DROP TABLE IF EXISTS album_artist CASCADE;
DROP TABLE IF EXISTS tracks CASCADE;
DROP TABLE IF EXISTS albums CASCADE;
DROP TABLE IF EXISTS artists CASCADE;
DROP TABLE IF EXISTS devices CASCADE;

CREATE TABLE artists (
                         id VARCHAR PRIMARY KEY,
                         name VARCHAR
);

CREATE TABLE albums (
                        id VARCHAR PRIMARY KEY,
                        name VARCHAR,
                        cover VARCHAR,
                        release_date DATE,
                        release_date_precision VARCHAR,
                        album_type VARCHAR
);

CREATE TABLE tracks (
                        id VARCHAR PRIMARY KEY,
                        name VARCHAR,
                        album_id VARCHAR REFERENCES albums(id),
                        duration_ms INTEGER,
                        is_explicit BOOLEAN,
                        is_local BOOLEAN
);

CREATE TABLE devices (
                         name VARCHAR PRIMARY KEY,
                         type VARCHAR
);

CREATE TABLE album_artist (
                              album_id VARCHAR REFERENCES albums(id),
                              artist_id VARCHAR REFERENCES artists(id),
                              PRIMARY KEY (album_id, artist_id)
);

CREATE TABLE track_artist (
                              track_id VARCHAR REFERENCES tracks(id),
                              artist_id VARCHAR REFERENCES artists(id),
                              PRIMARY KEY (track_id, artist_id)
);

CREATE TABLE track_history (
                               id SERIAL PRIMARY KEY,
                               context_type VARCHAR,
                               album_id VARCHAR REFERENCES albums(id),
                               track_id VARCHAR REFERENCES tracks(id),
                               device_name VARCHAR REFERENCES devices(name),
                               current_popularity INTEGER,
                               time_finished TIMESTAMP
);