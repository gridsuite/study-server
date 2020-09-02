CREATE TABLE IF NOT EXISTS study.study (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    subject text,
    isPrivate boolean,
    PRIMARY KEY (studyName, subject)
);

CREATE TABLE IF NOT EXISTS study.studyBySubject (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    subject text,
    isPrivate boolean,
    PRIMARY KEY (subject, isPrivate, studyName)
);

CREATE TABLE IF NOT EXISTS study.studyByAccessRights (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    subject text,
    isPrivate boolean,
    PRIMARY KEY (isPrivate, studyName)
);
