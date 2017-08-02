/*
 * generated by Xtext 2.10.0
 */
package org.structs4java.scoping

import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.xtext.scoping.impl.ImportScope
import org.structs4java.structs4JavaDsl.Structs4JavaDslPackage
import org.structs4java.structs4JavaDsl.StructDeclaration
import org.eclipse.xtext.scoping.Scopes

/**
 * This class contains custom scoping description.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#scoping
 * on how and when to use it.
 */
class Structs4JavaDslScopeProvider extends AbstractStructs4JavaDslScopeProvider {

	override getScope(EObject context, EReference reference) {
		if(context instanceof StructDeclaration &&
			reference == Structs4JavaDslPackage.Literals.STRUCT_DECLARATION__IMPLEMENTS) {
				
			val scope = super.getScope(context, reference)
			println("Scope for " + reference + " is: " + scope)
			return scope
		}
		
		super.getScope(context, reference)
	}
	
	
}
