package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.server.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;

/** Access persistence for college-administrator assignments. */
final class AccessStudentCollegeAdministrationRepository
        implements StudentCollegeAdministrationRepository {
    @Override public void requireDepartment(Connection c,String id,long version){
        try(var s=c.prepareStatement("SELECT isActive,rowVersion FROM tblDepartment WHERE departmentId=?")){s.setString(1,id);try(var r=s.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");if(r.getLong(2)!=version)throw new ConcurrentModificationException("COMMON_CONCURRENT_MODIFICATION");}}catch(SQLException e){throw failure(e);}}
    @Override public void requireAdministrator(Connection c,String id){
        try(var s=c.prepareStatement("SELECT roleCode,accountStatus FROM tblUser WHERE userId=?")){s.setString(1,id);try(var r=s.executeQuery()){if(!r.next()||!"COLLEGE_ADMIN".equals(r.getString(1))||!"ACTIVE".equals(r.getString(2)))throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");}}catch(SQLException e){throw failure(e);}}
    @Override public void requireUnassigned(Connection c,String id){
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM tblStudentCollegeAdministrator WHERE userId=? AND isActive=TRUE")){s.setString(1,id);try(var r=s.executeQuery()){r.next();if(r.getLong(1)>0)throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");}}catch(SQLException e){throw failure(e);}}
    @Override public void requireAssignment(Connection c,String department,String user,long version){
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM tblStudentCollegeAdministrator WHERE departmentId=? AND userId=? AND isActive=TRUE AND rowVersion=?")){s.setString(1,department);s.setString(2,user);s.setLong(3,version);try(var r=s.executeQuery()){r.next();if(r.getLong(1)!=1)throw new ConcurrentModificationException("COMMON_CONCURRENT_MODIFICATION");}}catch(SQLException e){throw failure(e);}}
    @Override public long countActive(Connection c,String department){
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM tblStudentCollegeAdministrator WHERE departmentId=? AND isActive=TRUE")){s.setString(1,department);try(var r=s.executeQuery()){r.next();return r.getLong(1);}}catch(SQLException e){throw failure(e);}}
    @Override public void assign(Connection c,String department,String user){
        try(var update=c.prepareStatement("UPDATE tblStudentCollegeAdministrator SET isActive=TRUE,rowVersion=rowVersion+1,updatedAt=NOW() WHERE departmentId=? AND userId=?")){update.setString(1,department);update.setString(2,user);if(update.executeUpdate()==0)try(var insert=c.prepareStatement("INSERT INTO tblStudentCollegeAdministrator (departmentId,userId,isActive,rowVersion,createdAt,updatedAt) VALUES (?,?,TRUE,0,NOW(),NOW())")){insert.setString(1,department);insert.setString(2,user);insert.executeUpdate();}}catch(SQLException e){throw failure(e);}}
    @Override public void deactivate(Connection c,String department,String user,long version){
        try(var s=c.prepareStatement("UPDATE tblStudentCollegeAdministrator SET isActive=FALSE,rowVersion=rowVersion+1,updatedAt=NOW() WHERE departmentId=? AND userId=? AND isActive=TRUE AND rowVersion=?")){s.setString(1,department);s.setString(2,user);s.setLong(3,version);if(s.executeUpdate()!=1)throw new ConcurrentModificationException("COMMON_CONCURRENT_MODIFICATION");}catch(SQLException e){throw failure(e);}}
    @Override public void transfer(Connection c,String source,String target,String user,long version){
        try(var s=c.prepareStatement("UPDATE tblStudentCollegeAdministrator SET departmentId=?,rowVersion=rowVersion+1,updatedAt=NOW() WHERE departmentId=? AND userId=? AND isActive=TRUE AND rowVersion=?")){s.setString(1,target);s.setString(2,source);s.setString(3,user);s.setLong(4,version);if(s.executeUpdate()!=1)throw new ConcurrentModificationException("COMMON_CONCURRENT_MODIFICATION");}catch(SQLException e){throw failure(e);}}
    private static PersistenceException failure(SQLException error){return new PersistenceException("College administration persistence failed",error);}
}
