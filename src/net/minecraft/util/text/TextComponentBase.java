package net.minecraft.util.text;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

public abstract class TextComponentBase implements ITextComponent
{
    /**
     * The later siblings of this component.  If this component turns the text bold, that will apply to all the siblings
     * until a later sibling turns the text something else.
     */
    protected List<ITextComponent> siblings = Lists.<ITextComponent>newArrayList();
    private Style style;

    /**
     * Adds a new component to the end of the sibling list, setting that component's style's parent style to this
     * component's style.
     *  
     * @return This component, for chaining (and not the newly added component)
     */
    public ITextComponent appendSibling(ITextComponent component)
    {
        component.getStyle().setParentStyle(this.getStyle());
        this.siblings.add(component);
        return this;
    }

    /**
     * Gets the sibling components of this one.
     */
    public List<ITextComponent> getSiblings()
    {
        return this.siblings;
    }

    /**
     * Adds a new component to the end of the sibling list, with the specified text. Same as calling {@link
     * #appendSibling(ITextComponent)} with a new {@link TextComponentString}.
     *  
     * @return This component, for chaining (and not the newly added component)
     */
    public ITextComponent appendText(String text)
    {
        return this.appendSibling(new TextComponentString(text));
    }

    /**
     * Sets the style of this component and updates the parent style of all of the sibling components.
     */
    public ITextComponent setStyle(Style style)
    {
        this.style = style;

        for (ITextComponent itextcomponent : this.siblings)
        {
            itextcomponent.getStyle().setParentStyle(this.getStyle());
        }

        return this;
    }

    /**
     * Gets the style of this component. Returns a direct reference; changes to this style will modify the style of this
     * component (IE, there is no need to call {@link #setStyle(Style)} again after modifying it).
     *  
     * If this component's style is currently <code>null</code>, it will be initialized to the default style, and the
     * parent style of all sibling components will be set to that style. (IE, changes to this style will also be
     * reflected in sibling components.)
     *  
     * This method never returns <code>null</code>.
     */
    public Style getStyle()
    {
        if (this.style == null)
        {
            this.style = new Style();

            for (ITextComponent itextcomponent : this.siblings)
            {
                itextcomponent.getStyle().setParentStyle(this.style);
            }
        }

        return this.style;
    }

    public Iterator<ITextComponent> iterator()
    {
        return Iterators.<ITextComponent>concat(Iterators.forArray(this), createDeepCopyIterator(this.siblings));
    }

    /**
     * Gets the text of this component <em>and all sibling components</em>, without any formatting codes.
     */
    public final String getUnformattedText()
    {
        StringBuilder stringbuilder = new StringBuilder();

        for (ITextComponent itextcomponent : this)
        {
            stringbuilder.append(itextcomponent.getUnformattedComponentText());
        }

        return stringbuilder.toString();
    }

    /**
     * Creates an iterator that iterates over the given components, returning deep copies of each component in turn so
     * that the properties of the returned objects will remain externally consistent after being returned.
     */
    public static Iterator<ITextComponent> createDeepCopyIterator(Iterable<ITextComponent> components)
    {
        Iterator<ITextComponent> iterator = Iterators.concat(Iterators.transform(components.iterator(), new Function<ITextComponent, Iterator<ITextComponent>>()
        {
            public Iterator<ITextComponent> apply(@Nullable ITextComponent p_apply_1_)
            {
                return p_apply_1_.iterator();
            }
        }));
        iterator = Iterators.transform(iterator, new Function<ITextComponent, ITextComponent>()
        {
            public ITextComponent apply(@Nullable ITextComponent p_apply_1_)
            {
                ITextComponent itextcomponent = p_apply_1_.createCopy();
                itextcomponent.setStyle(itextcomponent.getStyle().createDeepCopy());
                return itextcomponent;
            }
        });
        return iterator;
    }

    public boolean equals(Object p_equals_1_)
    {
        if (this == p_equals_1_)
        {
            return true;
        }
        else if (!(p_equals_1_ instanceof TextComponentBase))
        {
            return false;
        }
        else
        {
            TextComponentBase textcomponentbase = (TextComponentBase)p_equals_1_;
            return this.siblings.equals(textcomponentbase.siblings) && this.getStyle().equals(textcomponentbase.getStyle());
        }
    }

    public int hashCode()
    {
        return 31 * this.style.hashCode() + this.siblings.hashCode();
    }

    public String toString()
    {
        return "BaseComponent{style=" + this.style + ", siblings=" + this.siblings + '}';
    }
}