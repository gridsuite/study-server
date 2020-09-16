CREATE TABLE IF NOT EXISTS study.study (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    PRIMARY KEY (studyName, userId)
);

CREATE TABLE IF NOT EXISTS study.studyBySubject (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    PRIMARY KEY (userId, isPrivate, studyName)
);

CREATE TABLE IF NOT EXISTS study.publicStudy (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    PRIMARY KEY (studyName, userId)
);
