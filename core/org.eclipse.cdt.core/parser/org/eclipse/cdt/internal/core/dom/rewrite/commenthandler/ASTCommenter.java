/*******************************************************************************
 * Copyright (c) 2008 Institute for Software, HSR Hochschule fuer Technik  
 * Rapperswil, University of applied sciences and others
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html  
 * 
 * Contributors: 
 * Institute for Software - initial API and implementation 
 ******************************************************************************/
package org.eclipse.cdt.internal.core.dom.rewrite.commenthandler;

import java.util.ArrayList;
import java.util.TreeMap;

import org.eclipse.cdt.core.dom.ast.IASTComment;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.rewrite.util.OffsetHelper;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;

/**
 * This is the starting point of the entire comment handling  process. The creation of the 
 * NodeCommentMap is based on the IASTTranslationUnit. From this TranslationUnit the comments 
 * are extracted and skipped if they belong not to the same workspace. An ASTCommenterVisitor 
 * is initialized with this collection of comments. And the visit process can start. 
 * 
 * @see org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommenter
 * @see org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap
 *  
 * @author Guido Zgraggen IFS 
 */
public class ASTCommenter {
	
	/**
	 * Creates a NodeCommentMap for the given TranslationUnit. This is the only way
	 * to get a NodeCommentMap which contains all the comments mapped against nodes.
	 * 
	 * @param transUnit TranslationUnit
	 * @return NodeCommentMap
	 */
	public static NodeCommentMap getCommentedNodeMap(IASTTranslationUnit transUnit){
		if(transUnit== null) {
			return new NodeCommentMap();
		}
		ArrayList<IASTComment> comments = removeNotNeededComments(transUnit);		
		if(comments == null || comments.size() == 0) {
			return new NodeCommentMap();
		}
		return addCommentsToCommentMap(transUnit, comments);
	}

	private static ArrayList<IASTComment> removeNotNeededComments(IASTTranslationUnit transUnit) {
		ArrayList<IASTComment> comments = getCommentsInWorkspace(transUnit);
		if (comments == null || comments.size() == 0) {
			return null;
		}
		ArrayList<IASTComment> com = removeAllPreprocessorComments(transUnit, comments);
		return com;
	}

	private static ArrayList<IASTComment> getCommentsInWorkspace(IASTTranslationUnit tu) {
		IASTComment[] comments = tu.getComments();
		ArrayList<IASTComment> commentsInWorksapce = new ArrayList<IASTComment>();

		if (comments == null || comments.length == 0) {
			return null;
		}

		for (IASTComment comment : comments) {
			if (isInWorkspace(comment)) {
				commentsInWorksapce.add(comment);
			}
		}
		return commentsInWorksapce;
	}

	private static ArrayList<IASTComment> removeAllPreprocessorComments(IASTTranslationUnit tu, ArrayList<IASTComment> comments) {
		IASTPreprocessorStatement[] preprocessorStatements = tu.getAllPreprocessorStatements();
		TreeMap<Integer,Object> treeOfPreProcessorLines = new TreeMap<Integer,Object>();

		for (IASTPreprocessorStatement statement : preprocessorStatements) {
			if (isInWorkspace(statement)) {
				treeOfPreProcessorLines.put(OffsetHelper.getStartingLineNumber(statement),null);
			}
		}

		ArrayList<IASTComment> commentsInCode = new ArrayList<IASTComment>();
		for (IASTComment comment : comments) {
			int comStartLineNumber = OffsetHelper.getStartingLineNumber(comment);
			if (treeOfPreProcessorLines.containsKey(comStartLineNumber)) {
				continue;
			}
			commentsInCode.add(comment);
		}
		return commentsInCode;
	}

	private static boolean isInWorkspace(IASTNode node) {
		IPath workspacePath = Platform.getLocation();
		IPath nodePath = new Path(node.getContainingFilename());
		return workspacePath.isPrefixOf(nodePath);
	}

	
	private static NodeCommentMap addCommentsToCommentMap(IASTTranslationUnit rootNode,	ArrayList<IASTComment> comments){
		NodeCommentMap commentMap = new NodeCommentMap();
		CommentHandler commHandler = new CommentHandler(comments);

		IASTDeclaration[] declarations = rootNode.getDeclarations();
		for (int i = 0; i < declarations.length; i++) {

			if (isInWorkspace(declarations[i])) {

				ASTCommenterVisitor commenter = new ASTCommenterVisitor(commHandler, commentMap);
				declarations[i].accept(commenter);
				
				//add remaining comments to the last declaration => Comments won't get lost
				if (i + 1 == declarations.length) {
					commenter.addRemainingComments(declarations[i]);
				}
			}
		}
		return commentMap;
	}	
}
