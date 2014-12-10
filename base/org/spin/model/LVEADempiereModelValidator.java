/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2014 E.R.P. Consultores y Asociados.                    *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpconsultoresyasociados.com               *
 *****************************************************************************/
package org.spin.model;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.SQLException;

import org.compiere.model.I_C_Cash;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MBPBankAccount;
import org.compiere.model.MCash;
import org.compiere.model.MCashLine;
import org.compiere.model.MClient;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MMovement;
import org.compiere.model.MPaySelectionCheck;
import org.compiere.model.MPayment;
import org.compiere.model.MSysConfig;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.X_C_Invoice;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a>
 *
 */
public class LVEADempiereModelValidator implements ModelValidator {

	/**
	 * *** Constructor ***
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 01/07/2014, 10:30:05
	 */
	public LVEADempiereModelValidator() {
		super();
	}

	/** Logger */
	private static CLogger log = CLogger.getCLogger(LVEADempiereModelValidator.class);
	/** Client */
	private int m_AD_Client_ID = -1;
	/**	Current Business Partner				*/
	private int m_Current_C_BPartner_ID 		= 	0;
	/**	Current Allocation						*/
	private MAllocationHdr m_Current_Alloc 		= 	null;
	/**	Current Invoice							*/
	private MInvoice m_Current_Invoice 			= 	null;
	/**	Grand Amount							*/
	private BigDecimal grandAmount				= 	Env.ZERO;
	/**	Multiplier								*/
	private BigDecimal multiplier				= 	Env.ZERO;
	/**	Open Amount								*/
	private BigDecimal openAmt 					= 	Env.ZERO;
	
	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		// client = null for global validator
		if (client != null) {
			m_AD_Client_ID = client.getAD_Client_ID();
			log.info(client.toString());
		} else {
			log.info("Initializing global validator: " + this.toString());
		}
		//	Add Timing change in C_Order and C_Invoice
		engine.addDocValidate(MInvoice.Table_Name, this);
		engine.addDocValidate(MAllocationHdr.Table_Name, this);
		engine.addDocValidate(I_C_Cash.Table_Name, this);
		engine.addModelChange(MCashLine.Table_Name, this);
	}

	@Override
	public int getAD_Client_ID() {
		return m_AD_Client_ID;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		log.info("AD_User_ID=" + AD_User_ID);
		return null;
	}
	
	@Override
	public String docValidate(PO po, int timing) {
		if(timing == TIMING_BEFORE_REVERSECORRECT){
			if(po.get_TableName().equals(X_C_Invoice.Table_Name)){
				return validCashLineReference(po.get_TrxName(), po.get_ID());
			}
		}else if (timing==TIMING_BEFORE_PREPARE)	{	//	Dixon Martinez Add Tax in Cash
			if(po.get_TableName().equals(MCash.Table_Name))
			{
				log.fine(MCash.Table_Name + " -- TIMING_BEFORE_PREPARE");
				if (MSysConfig.getBooleanValue("TAX_ACCT_CASH", false))
				{
					MCash cash = (MCash) po;
					if (!MLVECashTax.calculateTaxTotal(cash)) // setTotals
						return Msg.translate(Env.getLanguage(Env.getCtx()), "TaxCalculatingError");
				}
			} 
		}
		else if(timing == TIMING_AFTER_COMPLETE ) {
			if(po.get_TableName().equals(X_C_Invoice.Table_Name)) {
				m_Current_Invoice = (MInvoice) po;
				grandAmount = Env.ZERO;m_Current_C_BPartner_ID = 0;
				MInvoiceLine [] m_InvoiceLine = m_Current_Invoice.getLines();
				int p_C_Invoice_ID = 0;
				
				int p_C_BPartner_ID = 0;
				for (MInvoiceLine mInvoiceLine : m_InvoiceLine) {
					if(mInvoiceLine.get_Value("DocAffected_ID") != null 
							&& mInvoiceLine.get_ValueAsInt("DocAffected_ID") > 0 )
						p_C_Invoice_ID = mInvoiceLine.get_ValueAsInt("DocAffected_ID");
					else 
						continue;
					p_C_BPartner_ID = m_Current_Invoice.getC_BPartner_ID();
					MInvoice m_InvoiceAffected = MInvoice.get(m_Current_Invoice.getCtx(), p_C_Invoice_ID);
					MDocType docType = (MDocType) m_InvoiceAffected.getC_DocType();
					String docBaseType = docType.getDocBaseType();
					try {
						CallableStatement cs = null;
						cs = DB.prepareCall("{call invoiceopen(?, 0, ?)}");
						cs.setInt(1, p_C_Invoice_ID);
						cs.registerOutParameter(2, java.sql.Types.NUMERIC);
						cs.execute();
						openAmt = cs.getBigDecimal(2);
					} catch (SQLException e) {
						e.printStackTrace();
					}
					multiplier = (docBaseType.substring(2).equals("C")? Env.ONE.negate(): Env.ONE)
							.multiply((docBaseType.substring(1,2).equals("P")? Env.ONE.negate(): Env.ONE));
					BigDecimal amt = mInvoiceLine.getLineNetAmt();
					BigDecimal newOpenAmt = (openAmt.subtract(amt)).multiply(multiplier);
					//
					if(p_C_BPartner_ID != m_Current_C_BPartner_ID) {
						completeAllocation();
					}
					grandAmount = grandAmount.add(amt);
					addAllocation(p_C_BPartner_ID, amt, openAmt, newOpenAmt, m_Current_Invoice, p_C_Invoice_ID);
				}
				completeAllocation();
				
			}else if(po.get_TableName().equals(MAllocationHdr.Table_Name)) {
				MInvoice.setIsPaid(m_Current_Invoice.getCtx(), m_Current_C_BPartner_ID, m_Current_Invoice.get_TrxName());
				m_Current_Invoice.saveEx();
			}
		} /*else if(timing == TIMING_AFTER_POST){
			if(po.get_TableName().equals(X_C_Invoice.Table_Name)) {
				MInvoice m_Invoice = (MInvoice) po;
				MInvoice.setIsPaid(m_Invoice.getCtx(), m_Invoice.getC_BPartner_ID(), m_Invoice.get_TrxName());
			}
		}*/
		//
		return null;
	}
	
	/**
	 * Complete Allocation
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 10/12/2014, 17:23:23
	 * @return void
	 */
	private void completeAllocation(){
		if(m_Current_Alloc != null){
			if(m_Current_Alloc.getDocStatus().equals(DocumentEngine.STATUS_Drafted)){
				log.fine("Amt Total Allocation=" + m_Current_Invoice.getGrandTotal());
				//	
				try {
					CallableStatement cs = null;
					cs = DB.prepareCall("{call invoiceopen(?, 0, ?)}");
					cs.setInt(1, m_Current_Invoice.get_ID());
					cs.registerOutParameter(2, java.sql.Types.NUMERIC);
					cs.execute();
					openAmt = cs.getBigDecimal(2);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				MDocType docType = (MDocType) m_Current_Invoice.getC_DocType();
				String docBaseType = docType.getDocBaseType();
				multiplier //DB.getSQLValueBD(m_Current_Invoice.get_TrxName(), sql, p_C_Invoice_ID);
					= (docBaseType.substring(2).equals("C")? Env.ONE.negate(): Env.ONE)
					.multiply((docBaseType.substring(1,2).equals("P")? Env.ONE.negate(): Env.ONE));
	
				BigDecimal amt = m_Current_Invoice.getGrandTotal();
				BigDecimal newOpenAmt = grandAmount.subtract(amt);
				
				MAllocationLine aLine = new MAllocationLine (m_Current_Alloc, grandAmount.multiply(multiplier), 
						Env.ZERO, Env.ZERO, newOpenAmt);
				aLine.setDocInfo(m_Current_C_BPartner_ID, 0, m_Current_Invoice.getC_Invoice_ID());
				aLine.saveEx();
				//	
				if(m_Current_Alloc.getDocStatus().equals(DocumentEngine.STATUS_Drafted)){
					log.fine("Current Allocation = " + m_Current_Alloc.getDocumentNo());
					//	
					m_Current_Alloc.setDocAction(DocumentEngine.ACTION_Complete);
					m_Current_Alloc.processIt(DocumentEngine.ACTION_Complete);
					m_Current_Alloc.saveEx();			
				}	
			}
			m_Current_Alloc = null;
			
		}
	}
	/**
	 * Add Document Allocation
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 10/12/2014, 17:23:45
	 * @param p_C_BPartner_ID
	 * @param amt
	 * @param openAmt
	 * @param newOpenAmt
	 * @param m_Invoice
	 * @param p_C_Invoice_ID
	 * @return void
	 */
	private void addAllocation(int p_C_BPartner_ID, BigDecimal amt,
			BigDecimal openAmt, BigDecimal newOpenAmt, MInvoice m_Invoice, int p_C_Invoice_ID) {
		if(m_Current_C_BPartner_ID != p_C_BPartner_ID){
			m_Current_Alloc = new MAllocationHdr(Env.getCtx(), true,	//	manual
					Env.getContextAsDate(m_Invoice.getCtx(), "#Date"), m_Invoice.getC_Currency_ID(), Env.getContext(Env.getCtx(), "#AD_User_Name"), m_Invoice.get_TrxName());
			m_Current_Alloc.setAD_Org_ID(m_Invoice.getAD_Org_ID());
			m_Current_Alloc.saveEx();
		}
		//	
		MAllocationLine aLine = new MAllocationLine (m_Current_Alloc, amt.multiply(multiplier), 
				Env.ZERO, Env.ZERO, newOpenAmt);
		aLine.setDocInfo(p_C_BPartner_ID, 0, p_C_Invoice_ID);
		aLine.saveEx();
		//
		m_Current_C_BPartner_ID = p_C_BPartner_ID;
	}

	/**
	 * Valid Reference in other record
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 01/07/2014, 10:45:30
	 * @param get_TrxName
	 * @param get_ID
	 * @return
	 * @return String
	 */
	private String validCashLineReference(String get_TrxName, int get_ID) {
		
		String m_ReferenceNo = DB.getSQLValueString(get_TrxName, "SELECT MAX(c.DocumentNo) " +
				"FROM C_Cash c " +
				"INNER JOIN C_CashLine cl ON (c.C_Cash_ID = cl.C_Cash_ID) " +
				"WHERE c.DocStatus IN('CO', 'CL') " +
				"AND cl.C_Invoice_ID = ?", get_ID);
		
		if(m_ReferenceNo != null)
			return "@SQLErrorReferenced@ @C_Cash_ID@: " + m_ReferenceNo;
		//	Return
		return null;	
	}

	@Override
	public String modelChange(PO po, int type) throws Exception {
		if(type == TYPE_BEFORE_NEW
				|| type == TYPE_BEFORE_CHANGE) {
			/*if(po.get_TableName().equals(MCashLine.Table_Name)) {
				MCashLine m_CashLine = (MCashLine) po;
				if(m_CashLine.getCashType().equals(MCashLine.CASHTYPE_Invoice)) {
					Timestamp ts = Env.getContextAsDate(Env.getCtx(), "DateAcct");     //  from C_Cash
					if (ts == null)
						ts = new Timestamp(System.currentTimeMillis());
					
					String sql = " SELECT C_BPartner_ID, C_Currency_ID, "
							+ " invoiceOpen(i.C_Invoice_ID, COALESCE(ips.C_InvoicePaySchedule_ID,0)), IsSOTrx,"
							+ " invoiceDiscount(i.C_Invoice_ID,?,COALESCE(ips.C_InvoicePaySchedule_ID,0))"
							+ " FROM C_Invoice i"
							+ " LEFT JOIN C_InvoicePaySchedule ips ON (i.C_Invoice_ID = ips.C_Invoice_ID)"
							+ " WHERE i.C_Invoice_ID = ?";
					
					int p_C_Invoice_ID = m_CashLine.getC_Invoice_ID();
					
					PreparedStatement pstmt = null;
					ResultSet rs = null;
					
					try {
						pstmt = DB.prepareStatement(sql,m_CashLine.get_TrxName());
						pstmt.setTimestamp(1, ts);
						pstmt.setInt(2, p_C_Invoice_ID);
						
						rs = pstmt.executeQuery();
						
						if(rs.next()) {
							m_CashLine.setC_Currency_ID(new Integer(rs.getInt(2)));
							BigDecimal PayAmt = rs.getBigDecimal(3);
							BigDecimal DiscountAmt = rs.getBigDecimal(5);
							boolean isSOTrx = "Y".equals(rs.getString(4));
							if (!isSOTrx) {
								PayAmt = PayAmt.negate();
								DiscountAmt = DiscountAmt.negate();
							}
							//
							m_CashLine.setAmount(PayAmt.subtract(DiscountAmt));
							m_CashLine.setDiscountAmt(DiscountAmt);
							m_CashLine.setWriteOffAmt(Env.ZERO);
						}
					} catch(SQLException e) {
						log.log(Level.SEVERE, "invoice", e);
						return e.getLocalizedMessage();
					} finally {
						DB.close(rs,pstmt);
						rs = null;
						pstmt = null;
					}*/
					//  Check, if InvTotalAmt exists
					/*String total = Env.getContext(Env.getCtx(), "InvTotalAmt");
					if (total == null || total.length() == 0)
						return "";
					BigDecimal InvTotalAmt = new BigDecimal(total);

					BigDecimal PayAmt = m_CashLine.getAmount();
					BigDecimal DiscountAmt = m_CashLine.getDiscountAmt();
					BigDecimal WriteOffAmt = m_CashLine.getWriteOffAmt();
					
					BigDecimal newAmount = (BigDecimal) (m_CashLine.get_Value("Amount") == null ? Env.ZERO : m_CashLine.get_Value("Amount"));
					BigDecimal oldAmount = (BigDecimal) (m_CashLine.get_ValueOld("Amount") == null ? Env.ZERO : m_CashLine.get_ValueOld("Amount"));
					
					if( newAmount.compareTo(oldAmount) == 0)  {
						WriteOffAmt = InvTotalAmt.subtract(PayAmt).subtract(DiscountAmt);
						m_CashLine.setWriteOffAmt( WriteOffAmt);
					} else {
						PayAmt = InvTotalAmt.subtract(DiscountAmt).subtract(WriteOffAmt);
						m_CashLine.setAmount(PayAmt);
					}*/
			/*	}
				return "";
			}*/
			
			if(po.get_TableName().equals(MMovement.Table_Name)) {
				MMovement m_Current_Movement = (MMovement) po;
				MDocType m_DocType = (MDocType) m_Current_Movement.getC_DocType();
				if(m_DocType.get_ValueAsBoolean("IsInTransit")) {
					m_Current_Movement.setIsInTransit(true);
					m_Current_Movement.saveEx();
				}
			}
		}
		//Carlos Parada Set BP_BankAccount to PaySelection if have Payment And Set Description From PaySelection
		if ((type == TYPE_AFTER_CHANGE || type == TYPE_AFTER_NEW)&& po.get_TableName().equals(MPaySelectionCheck.Table_Name)){
			MPaySelectionCheck psch = (MPaySelectionCheck) po;
			if (psch.getC_Payment_ID()!=0){
				//Set Bank Account
				if (psch.getC_BP_BankAccount_ID()==0){
					MBPBankAccount[] bpas = MBPBankAccount.getOfBPartner (Env.getCtx(), psch.getC_BPartner_ID());
					for (int i =0;i<bpas.length;i++)
						if (psch.getPaymentRule().equals(bpas[i].get_ValueAsString("PaymentRule")) &&
								bpas[i].get_ValueAsBoolean("IsDefault")){
							psch.setC_BP_BankAccount_ID(bpas[i].getC_BP_BankAccount_ID());
							psch.save();
						}
				}
				//Set Description From PaySelection
				if (psch.getC_Payment().getDescription()==null && psch.getC_PaySelection().getDescription()!=null) {
					MPayment pay = (MPayment)psch.getC_Payment();
					pay.setDescription(psch.getC_PaySelection().getDescription());
					pay.save();
				}
			}
		}

		return null;
	}
}
