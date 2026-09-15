package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Student-editable personal attributes and contact details. */
/**
 * Carries immutable student personal profile data.
 * @param namePinyin the name pinyin
 * @param formerName the former name
 * @param politicalStatus the political status
 * @param ethnicity the ethnicity
 * @param maritalStatus the marital status
 * @param idDocumentType the id document type
 * @param idDocumentNumber the id document number
 * @param idIssuedDate the id issued date
 * @param birthDate the birth date
 * @param nativePlace the native place
 * @param countryRegion the country region
 * @param birthplace the birthplace
 * @param studentOriginPlace the student origin place
 * @param householdRegistrationType the household registration type
 * @param householdBeforeEnrollment the household before enrollment
 * @param householdAfterEnrollment the household after enrollment
 * @param overseasChineseStatus the overseas chinese status
 * @param religion the religion
 * @param leagueMember the league member
 * @param leagueJoinDate the league join date
 * @param partyMember the party member
 * @param partyJoinDate the party join date
 * @param healthStatus the health status
 * @param bloodType the blood type
 * @param weightKg the weight kg
 * @param heightCm the height cm
 * @param specialties the specialties
 * @param hobbies the hobbies
 * @param onlyChild the only child
 * @param email the email
 * @param phone the phone
 */
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
