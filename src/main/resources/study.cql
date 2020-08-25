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
    subject text,
    studyName text,
    PRIMARY KEY (subject, studyName)
);

CREATE TABLE IF NOT EXISTS study.studyByAccessRights (
    isPrivate boolean,
    studyName text,
    PRIMARY KEY (isPrivate, studyName)
);
