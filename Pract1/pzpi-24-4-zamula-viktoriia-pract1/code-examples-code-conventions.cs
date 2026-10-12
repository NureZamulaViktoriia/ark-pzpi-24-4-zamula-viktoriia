using System;
using System.Text;
using Xunit;

namespace Contoso.Orders;

// В.4 
public class OrderReport
{
    private readonly StringBuilder _text = new();
}

public class StringCalculator
{
    public int Add(string numbers)
    {
        if (string.IsNullOrEmpty(numbers))
        {
            return 0;
        }

        var sum = 0;
        foreach (var part in numbers.Split(','))
        {
            sum += int.Parse(part);
        }

        return sum;
    }
}

// В.1 
public class ExampleOne
{
    void Move(int startX, int endX, int previousX)
    {
        if ((startX > endX) && (startX > previousX))
        {
            // Take appropriate action.
        }
    }
}

// В.2 
public class ExampleTwo
{
    public int f(int a, int b)
    {
        int x = a * b; // multiply
        if (x > 100) { return x - 10; }
        else { return x; }
    }
}

// В.3 
public class ExampleThree
{
    private const int Threshold = 100;
    private const int Discount = 10;

    public int GetTotal(int price, int quantity)
    {
        var total = price * quantity;
        return total > Threshold ? total - Discount : total;
    }
}

// В.5 
public interface IWorkerQueue
{
    int Count { get; }
}

public class DataService(IWorkerQueue workerQueue)
{
    private const int MaxItems = 100;
    public bool IsFull => workerQueue.Count >= MaxItems;
}

// В.6 
public class ExampleSix
{
    private int counter;
    private readonly string request = "ping";

    private void Send(string value)
    {
    }

    public void Run()
    {
        // Redundant: repeats what the code already says.
        counter++; // Add one to counter.

        // Useful: explains why, not what.
        // The service drops the first request after a restart,
        // so the request is sent twice.
        Send(request);
        Send(request);
    }
}

// В.7
public class ExampleSeven
{
    /// <summary>Adds two integers.</summary>
    /// <param name="first">The first addend.</param>
    /// <param name="second">The second addend.</param>
    /// <returns>The sum of the two numbers.</returns>
    /// <example>
    /// <code>var sum = Add(1, 2);</code>
    /// </example>
    public static int Add(int first, int second) => first + second;
}

// В.8 
// root = true
//
// [*.cs]
// indent_style = space
// indent_size = 4
// csharp_new_line_before_open_brace = all
// dotnet_diagnostic.IDE0055.severity = warning

// В.9
public class ExampleNine
{
    [Fact]
    public void Add_EmptyString_ReturnsZero()
    {
        // Arrange
        var stringCalculator = new StringCalculator();
        // Act
        var actual = stringCalculator.Add("");
        // Assert
        Assert.Equal(0, actual);
    }
}

// В.10 
// # Format all code in the solution.
// dotnet format ./solution.sln
//
// # Verify that all code is correctly formatted.
// dotnet format --verify-no-changes

// В.11
public class ExampleEleven
{
    public interface IDateTimeProvider
    {
        DayOfWeek DayOfWeek();
    }

    public int GetDiscountedPrice(
        int price, IDateTimeProvider provider)
    {
        return provider.DayOfWeek() == DayOfWeek.Tuesday
            ? price / 2
            : price;
    }
}

// В.12 
// <PropertyGroup>
//   <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>
// </PropertyGroup>

// В.13
public class ExampleThirteen
{
    [Theory]
    [InlineData("0,0,0", 0)]
    [InlineData("0,1,2", 3)]
    [InlineData("1,2,3", 6)]
    public void Add_MultipleNumbers_ReturnsSum(
        string input, int expected)
    {
        var calculator = new StringCalculator();
        Assert.Equal(expected, calculator.Add(input));
    }
}
