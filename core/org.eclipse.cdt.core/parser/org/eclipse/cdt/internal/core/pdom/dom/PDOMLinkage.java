/*******************************************************************************
 * Copyright (c) 2005, 2008 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX - Initial API and implementation
 *     Markus Schorn (Wind River Systems)
 *     IBM Corporation
 *     Andrew Ferguson (Symbian)
 *******************************************************************************/

package org.eclipse.cdt.internal.core.pdom.dom;

import org.eclipse.cdt.core.dom.ILinkage;
import org.eclipse.cdt.core.dom.IName;
import org.eclipse.cdt.core.dom.IPDOMVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IArrayType;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IField;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IPointerType;
import org.eclipse.cdt.core.dom.ast.IProblemBinding;
import org.eclipse.cdt.core.dom.ast.IQualifierType;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassScope;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPDeferredTemplateInstance;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPDelegate;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPNamespaceAlias;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPNamespaceScope;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPSpecialization;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateInstance;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateParameter;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateScope;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.core.index.IIndexLinkage;
import org.eclipse.cdt.internal.core.Util;
import org.eclipse.cdt.internal.core.dom.parser.ASTInternal;
import org.eclipse.cdt.internal.core.index.IIndexBindingConstants;
import org.eclipse.cdt.internal.core.index.IIndexScope;
import org.eclipse.cdt.internal.core.index.composite.CompositeScope;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.WritablePDOM;
import org.eclipse.cdt.internal.core.pdom.db.BTree;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeComparator;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeVisitor;
import org.eclipse.cdt.internal.core.pdom.db.IString;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Doug Schaefer
 * 
 * This class represents a collection of symbols that can be linked together at
 * link time. These are generally global symbols specific to a given language.
 */
public abstract class PDOMLinkage extends PDOMNamedNode implements IIndexLinkage, IIndexBindingConstants {

	// record offsets
	private static final int ID_OFFSET   = PDOMNamedNode.RECORD_SIZE + 0;
	private static final int NEXT_OFFSET = PDOMNamedNode.RECORD_SIZE + 4;
	private static final int INDEX_OFFSET = PDOMNamedNode.RECORD_SIZE + 8;
	private static final int NESTED_BINDINGS_INDEX = PDOMNamedNode.RECORD_SIZE + 12;

	@SuppressWarnings("hiding")
	protected static final int RECORD_SIZE = PDOMNamedNode.RECORD_SIZE + 16;

	// node types
	protected static final int LINKAGE= 0; // special one for myself

	public PDOMLinkage(PDOM pdom, int record) {
		super(pdom, record);
	}

	protected PDOMLinkage(PDOM pdom, String languageId, char[] name) throws CoreException {
		super(pdom, null, name);
		Database db = pdom.getDB();

		// id
		db.putInt(record + ID_OFFSET, db.newString(languageId).getRecord());

		pdom.insertLinkage(this);
	}

	protected int getRecordSize() {
		return RECORD_SIZE;
	}

	public int getNodeType() {
		return LINKAGE;
	}

	public static IString getId(PDOM pdom, int record) throws CoreException {
		Database db = pdom.getDB();
		int namerec = db.getInt(record + ID_OFFSET);
		return db.getString(namerec);
	}

	public static int getNextLinkageRecord(PDOM pdom, int record) throws CoreException {
		return pdom.getDB().getInt(record + NEXT_OFFSET);
	}

	public void setNext(int nextrec) throws CoreException {
		pdom.getDB().putInt(record + NEXT_OFFSET, nextrec);
	}

	public BTree getIndex() throws CoreException {
		return new BTree(pdom.getDB(), record + INDEX_OFFSET, getIndexComparator());
	}

	/**
	 * Returns the BTree for the nested bindings.
	 * @return
	 * @throws CoreException
	 */
	public BTree getNestedBindingsIndex() throws CoreException {
		return new BTree(getPDOM().getDB(), record + NESTED_BINDINGS_INDEX, getNestedBindingsComparator());
	}

	public void accept(final IPDOMVisitor visitor) throws CoreException {
		if (visitor instanceof IBTreeVisitor) {
			getIndex().accept((IBTreeVisitor) visitor);
		} else {
			getIndex().accept(new IBTreeVisitor() {
				public int compare(int record) throws CoreException {
					return 0;
				}
				public boolean visit(int record) throws CoreException {
					PDOMNode node= getNode(record);
					if (node != null) {
						if (visitor.visit(node))
							node.accept(visitor);
						visitor.leave(node);
					}
					return true;
				}
			});
		}
	}

	public ILinkage getLinkage() throws CoreException {
		return this;
	}

	public final void addChild(PDOMNode child) throws CoreException {
		getIndex().insert(child.getRecord());
	}

	public PDOMNode getNode(int record) throws CoreException {
		switch (PDOMNode.getNodeType(pdom, record)) {
		case POINTER_TYPE:
			return new PDOMPointerType(pdom, record);
		case ARRAY_TYPE:
			return new PDOMArrayType(pdom, record);
		case QUALIFIER_TYPE:
			return new PDOMQualifierType(pdom, record);
		}
		return null;
	}

	public PDOMNode addType(PDOMNode parent, IType type) throws CoreException {
		if (type instanceof IPointerType)
			return new PDOMPointerType(pdom, parent, (IPointerType)type);
		else if (type instanceof IArrayType) 
			return new PDOMArrayType(pdom, parent, (IArrayType) type);
		else if (type instanceof IQualifierType)
			return new PDOMQualifierType(pdom, parent, (IQualifierType)type);
		else
			return null;
	}

	public abstract IBTreeComparator getIndexComparator();

	public IBTreeComparator getNestedBindingsComparator() {
		return new FindBinding.NestedBindingsBTreeComparator(this);
	}

	public abstract PDOMBinding addBinding(IASTName name) throws CoreException;

	public abstract PDOMBinding addBinding(IBinding binding, IASTName fromName) throws CoreException;

	public final PDOMBinding adaptBinding(final IBinding inputBinding) throws CoreException {
		if (inputBinding == null || inputBinding instanceof IProblemBinding) {
			return null;
		}

		boolean isFromAST= true;
		IBinding binding= inputBinding;
		if (binding instanceof PDOMBinding) {
			// there is no guarantee, that the binding is from the same PDOM object.
			PDOMBinding pdomBinding = (PDOMBinding) binding;
			if (pdomBinding.getPDOM() == getPDOM()) {
				return pdomBinding;
			}
			// if the binding is from another pdom it has to be adapted. However don't adapt file-local bindings
			if (pdomBinding.isFileLocal()) {
				return null;
			}
			isFromAST= false;
		}

		PDOMBinding result= (PDOMBinding) pdom.getCachedResult(inputBinding);
		if (result != null) {
			return result;
		}

		int fileLocalRec= 0;
		if (isFromAST) {
			// assign names to anonymous types.
			binding= PDOMASTAdapter.getAdapterIfAnonymous(binding);
			if (binding == null) {
				return null;
			}
			PDOMFile lf= getLocalToFile(binding);
			if (lf != null) {
				fileLocalRec= lf.getRecord();
			}
		}
		result= doAdaptBinding(binding, fileLocalRec);
		if (result != null) {
			pdom.putCachedResult(inputBinding, result);
		}
		return result;
	}

	protected abstract PDOMBinding doAdaptBinding(IBinding binding, int fileLocalRec) throws CoreException;

	public final PDOMBinding resolveBinding(IASTName name) throws CoreException {
		IBinding binding= name.resolveBinding();
		if (binding != null) {
			return adaptBinding(binding);
		}
		return null;
	}

	/**
	 * 
	 * @param binding
	 * @return <ul><li> null - skip this binding (don't add to pdom)
	 * <li>this - for filescope
	 * <li>a PDOMBinding instance - parent adapted binding
	 * </ul>
	 * @throws CoreException
	 */
	protected PDOMNode getAdaptedParent(IBinding binding, boolean addParent) throws CoreException {
		try {
			IBinding scopeBinding = null;
			if (binding instanceof ICPPTemplateInstance) {
				scopeBinding = ((ICPPTemplateInstance)binding).getTemplateDefinition();
			} else {
				// in case this is a delegate the scope of the delegate can be different to the
				// scope of the delegating party (e.g. using-declarations)
				while (binding instanceof ICPPDelegate && !(binding instanceof ICPPNamespaceAlias)) {
					binding= ((ICPPDelegate) binding).getBinding();
				}
				IScope scope = binding.getScope();
				if (scope == null) {
					if (binding instanceof ICPPDeferredTemplateInstance) {
						ICPPDeferredTemplateInstance deferred = (ICPPDeferredTemplateInstance) binding;
						ICPPTemplateDefinition template = deferred.getTemplateDefinition();
						scope = template.getScope();
					} 

					IIndexBinding ib = (binding instanceof IIndexBinding) ? (IIndexBinding) binding : null;

					if (ib == null && binding instanceof ICPPSpecialization) {
						IBinding spec = ((ICPPSpecialization)binding).getSpecializedBinding();
						if (spec instanceof IIndexBinding) {
							ib = (IIndexBinding) spec;
						}
					}

					if (ib != null) {
						// don't adapt file local bindings from other fragments to this one.
						if (ib.isFileLocal()) {
							return null;
						}
						// in an index the null scope represents global scope.
						return this;
					}

					return null;
				}

				if (scope instanceof IIndexScope) {
					if (scope instanceof CompositeScope) { // we special case for performance
						return adaptBinding(((CompositeScope)scope).getRawScopeBinding());
					} else {
						return adaptBinding(((IIndexScope) scope).getScopeBinding());
					}
				}

				// the scope is from the ast
				if (scope instanceof ICPPTemplateScope && !(binding instanceof ICPPTemplateParameter || binding instanceof ICPPTemplateInstance)) {
					scope = scope.getParent();
					if (scope == null) {
						return null;
					}
				}

				if (scope instanceof ICPPNamespaceScope) {
					IName name= scope.getScopeName();
					if (name != null && name.toCharArray().length == 0) {
						// skip unnamed namespaces
						return null;
					}
				}

				IASTNode scopeNode = ASTInternal.getPhysicalNodeOfScope(scope);
				if (scopeNode instanceof IASTCompoundStatement) {
					return null;
				} else if (scopeNode instanceof IASTTranslationUnit) {
					return this;
				} else {
					if (scope instanceof ICPPClassScope) {
						scopeBinding = ((ICPPClassScope)scope).getClassType();
					} else {
						IName scopeName = scope.getScopeName();
						if (scopeName instanceof IASTName) {
							scopeBinding = ((IASTName) scopeName).resolveBinding();
						}
					}
				}
			}
			if (scopeBinding != null && scopeBinding != binding) {
				PDOMBinding scopePDOMBinding = null;
				if (addParent) {
					scopePDOMBinding = addBinding(scopeBinding, null);
				} else {
					scopePDOMBinding = adaptBinding(scopeBinding);
				}
				if (scopePDOMBinding != null)
					return scopePDOMBinding;
			}
		} catch (DOMException e) {
			throw new CoreException(Util.createStatus(e));
		}
		return null;
	}

	protected PDOMFile getLocalToFile(IBinding binding) throws CoreException {
		if (pdom instanceof WritablePDOM) {
			final WritablePDOM wpdom= (WritablePDOM) pdom;
			try {
				if (binding instanceof IField) {
					return null;
				}
				boolean isFileLocal= false;
				if (binding instanceof IVariable) {
					if (!(binding instanceof IField)) {
						isFileLocal= ASTInternal.isStatic((IVariable) binding);
					}
				} else if (binding instanceof IFunction) {
					IFunction f= (IFunction) binding;
					isFileLocal= ASTInternal.isStatic(f, false);
				} 

				if (isFileLocal) {
					String path= ASTInternal.getDeclaredInSourceFileOnly(binding);
					if (path != null) {
						return wpdom.getFileForASTPath(getLinkageID(), path);
					}
				}
			} catch (DOMException e) {
			}
		}
		return null;
	}

	public abstract int getBindingType(IBinding binding);

	/**
	 * Callback informing the linkage that a name has been added. This is
	 * used to do additional processing, like establishing inheritance relationships.
	 * @param pdomName the name that was inserted into the linkage
	 * @param name the name that caused the insertion
	 * @throws CoreException 
	 * @since 4.0
	 */
	public void onCreateName(PDOMName pdomName, IASTName name) throws CoreException {
		IASTNode parentNode= name.getParent();
		if (parentNode instanceof IASTDeclSpecifier) {
			IASTDeclSpecifier ds= (IASTDeclSpecifier) parentNode;
			if (ds.getStorageClass() == IASTDeclSpecifier.sc_typedef) {
				if (pdomName.getEnclosingDefinitionRecord() != 0) {
					pdomName.setIsBaseSpecifier(true);
				}
			}
		}
	}

	/**
	 * Callback informing the linkage that a name is about to be deleted. This is
	 * used to do additional processing, like removing inheritance relationships.
	 * @param pdomName the name that was inserted into the linkage
	 * @param name the name that caused the insertion
	 * @throws CoreException 
	 * @since 4.0
	 */
	public void onDeleteName(PDOMName nextName) throws CoreException {
	}

	/**
	 * Callback informing the linkage that a binding has been added. Used to index nested bindings.
	 * @param pdomBinding
	 * @throws CoreException
	 * @since 4.0.1
	 */
	public void afterAddBinding(PDOMBinding pdomBinding) throws CoreException {
		if (pdomBinding.getParentNodeRec() != record) {
			getNestedBindingsIndex().insert(pdomBinding.getRecord());
		}
	}

	/**
	 * Callback informing the linkage that a binding is about to be removed. Used to index nested bindings.
	 * @param pdomBinding
	 * @throws CoreException
	 * @since 4.0.1
	 */
	public void beforeRemoveBinding(PDOMBinding pdomBinding) throws CoreException {
		if (pdomBinding.getParentNodeRec() != record) {
			getNestedBindingsIndex().delete(pdomBinding.getRecord());
		}
	}

	public void deleteType(IType type, int ownerRec) throws CoreException {
		if (type instanceof PDOMNode) {
			PDOMNode node= (PDOMNode) type;
			// at this point only delete types that are actually owned by the requesting party.
			if (node.getParentNodeRec() == ownerRec) {
				assert ! (node instanceof IBinding);
				node.delete(this);
			}
		}
	}

	public void deleteBinding(IBinding binding) throws CoreException {
		// no implementation, yet.
	}

	public void delete(PDOMLinkage linkage) throws CoreException {
		assert false; // no need to delete linkages.
	}
}
