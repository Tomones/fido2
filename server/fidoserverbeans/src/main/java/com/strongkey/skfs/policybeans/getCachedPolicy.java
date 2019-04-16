/**
 * Copyright StrongAuth, Inc. All Rights Reserved.
 *
 * Use of this source code is governed by the Gnu Lesser General Public License 2.3.
 * The license can be found at https://github.com/StrongKey/FIDO-Server/LICENSE
 */

package com.strongkey.skfs.policybeans;

import com.strongkey.skfe.entitybeans.FidoKeys;
import com.strongkey.skfs.fido.policyobjects.FidoPolicyObject;
import com.strongkey.skfs.utilities.SKFEException;
import com.strongkey.skfs.utilities.skfsConstants;
import com.strongkey.skce.utilities.skceMaps;
import com.strongkey.skfs.txbeans.getFidoKeysLocal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ejb.EJB;
import javax.ejb.Stateless;

@Stateless
public class getCachedPolicy implements getCachedPolicyLocal {
    
    @EJB
    getFidoKeysLocal getFidoKeysBean;

    @Override
    public FidoPolicyObject getByDidUsername(Long did, String username){       
        FidoKeys fk = null;
        try {
            fk = getFidoKeysBean.getNewestKeyByUsernameStatus(did, username, "Active");
        } catch (SKFEException ex) {
            Logger.getLogger(getCachedPolicy.class.getName()).log(Level.SEVERE, null, ex);
        }
        return lookupPolicyFromNewestKey(did, fk);
    }
    
    @Override
    public FidoPolicyObject getMapKey(String policyMapKey) {
        return (FidoPolicyObject) skceMaps.getMapObj().get(skfsConstants.MAP_FIDO_POLICIES, policyMapKey);
    }
    
    
    //TODO optimize. Current thought is that inefficiently performing a look up on less than
    //10 Active policies is cheaper than efficiently looking up the correct policy
    //from the DB and parsing from the DB into an object.
    //TODO if the policy's end date has passed, the policy should be set to Inactive.
    private FidoPolicyObject lookupPolicyFromNewestKey(Long did, FidoKeys fk){
        //Only check policies from the listed domain, that have started, and whose end_date has not passed
        Date currentDate = new Date();
        Collection<FidoPolicyObject> fpCol
                = ((Collection<FidoPolicyObject>) skceMaps.getMapObj().values(skfsConstants.MAP_FIDO_POLICIES))
                .stream()
                .filter(fp -> fp.getDid().equals(did))
                .filter(fp -> fp.getStartDate().before(currentDate))
                .filter(fp -> fp.getEndDate() == null || fp.getEndDate().after(currentDate))
                .collect(Collectors.toList());
        
        //If the user has no registered keys, return policy with the latest start_date
        if(fk == null){
            return findNewestPolicy(fpCol);
        }
        else{   //attempt to find policy based on registration time of key
            FidoPolicyObject result = findPolicyDuringRegistration(fpCol, fk);

            if (result == null) {
                return findOldestPolicySinceKeyCreation(fpCol, fk);
            }

            return result;
        }
    }
    
    //Find the newest Active Policy
    private FidoPolicyObject findNewestPolicy(Collection<FidoPolicyObject> fpCol) {
        try {
            return fpCol.stream()
                    .max(Comparator.comparing(FidoPolicyObject::getStartDate))
                    .get();
        } catch (NullPointerException | NoSuchElementException ex) {
            return null;
        }
    }
    
    //Find the Active policy whose start_date is before the key's creation date
    //and whose end_date is after the key's creation date. If multiple policies
    //are found, use returns the policy with the later start_date. If no policies
    //are found, return null
    private FidoPolicyObject findPolicyDuringRegistration(Collection<FidoPolicyObject> fpCol, FidoKeys fk){
        try {
            return fpCol.stream()
                    .filter(fp -> fp.getStartDate().before(fk.getCreateDate()))
                    .filter(fp -> fp.getEndDate() == null || fp.getEndDate().after(fk.getCreateDate()))
                    .max(Comparator.comparing(FidoPolicyObject::getStartDate))
                    .get();
        } catch (NullPointerException | NoSuchElementException ex) {
            return null;
        }
    }
    
    //Return the first Active policy whose start_date is after the key's creation date
    private FidoPolicyObject findOldestPolicySinceKeyCreation(Collection<FidoPolicyObject> fpCol, FidoKeys fk){
        try {
            return fpCol.stream()
                    .filter(fp -> fp.getStartDate().after(fk.getCreateDate()))
                    .min(Comparator.comparing(FidoPolicyObject::getStartDate))
                    .get();
        } catch (NullPointerException | NoSuchElementException ex) {
            return null;
        }
    }
}
