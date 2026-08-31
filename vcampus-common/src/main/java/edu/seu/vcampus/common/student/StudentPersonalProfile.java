package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Student-editable personal attributes and contact details. */
public record StudentPersonalProfile(
        String namePinyin,
        String formerName,
        String politicalStatus,
        String ethnicity,
        String maritalStatus,
        String idDocumentType,
        String idDocumentNumber,
        LocalDate idIssuedDate,
        LocalDate birthDate,
        String nativePlace,
        String countryRegion,
        String birthplace,
        String studentOriginPlace,
        String householdRegistrationType,
        String householdBeforeEnrollment,
        String householdAfterEnrollment,
        String overseasChineseStatus,
        String religion,
        boolean leagueMember,
        LocalDate leagueJoinDate,
        boolean partyMember,
        LocalDate partyJoinDate,
        String healthStatus,
        String bloodType,
        Integer weightKg,
        Integer heightCm,
        String specialties,
        String hobbies,
        boolean onlyChild,
        String email,
        String phone) implements Serializable { }
